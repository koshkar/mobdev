package io.github.mobdev

import android.annotation.SuppressLint
import android.content.Context
import android.database.Cursor
import android.provider.ContactsContract
import android.util.Log
import androidx.core.database.getStringOrNull

@SuppressLint("Range")
fun Context.fetchAllContacts(): List<Contact> {
    Log.d("FETCH", "fetchAllContacts called")

    val emailsByContact = mutableMapOf<Long, String>()
    contentResolver.query(
        ContactsContract.CommonDataKinds.Email.CONTENT_URI,
        arrayOf(
            ContactsContract.CommonDataKinds.Email.CONTACT_ID,
            ContactsContract.CommonDataKinds.Email.ADDRESS
        ),
        null,
        null,
        null
    )?.use { cursor: Cursor ->
        while (cursor.moveToNext()) {
            val id = cursor.getLong(0)
            val email = cursor.getStringOrNull(1)
            if (!email.isNullOrBlank() && id !in emailsByContact) {
                emailsByContact[id] = email
            }
        }
    }

    return contentResolver.query(
        ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
        null,
        null,
        null,
        ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC"
    ).use { cursor: Cursor? ->
        if (cursor == null) return emptyList()
        buildList {
            while (cursor.moveToNext()) {
                val id = cursor.getLong(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
                )
                val name = cursor.getStringOrNull(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                )
                val phoneNumber = cursor.getStringOrNull(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                )
                add(Contact(id, name, phoneNumber, emailsByContact[id]))
            }
        }
    }
}
