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
                val email = cursor.getStringOrNull(
                    cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.ADDRESS)
                )
                add(Contact(id, name, phoneNumber, email))
            }
        }
    }
}
