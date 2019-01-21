/*
 * © 2016-2019 FlowCrypt Limited. Limitations apply. Contact human@flowcrypt.com
 * Contributors: DenBond7
 */

package com.flowcrypt.email.ui.adapter;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CursorAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.flowcrypt.email.R;
import com.flowcrypt.email.database.dao.source.ContactsDaoSource;

/**
 * This adapter describes logic to prepare show contacts from the database.
 *
 * @author DenBond7
 * Date: 26.05.2017
 * Time: 18:00
 * E-mail: DenBond7@gmail.com
 */

public class ContactsListCursorAdapter extends CursorAdapter {
  private OnDeleteContactListener listener;
  private boolean isDeleteEnabled;

  public ContactsListCursorAdapter(Context context, Cursor c, boolean autoRequery,
                                   OnDeleteContactListener listener) {
    this(context, c, autoRequery, listener, true);
  }

  public ContactsListCursorAdapter(Context context, Cursor c, boolean autoRequery,
                                   OnDeleteContactListener listener, boolean isDeleteEnabled) {
    super(context, c, autoRequery);
    this.listener = listener;
    this.isDeleteEnabled = isDeleteEnabled;
  }

  @Override
  public View newView(Context context, Cursor cursor, ViewGroup parent) {
    return LayoutInflater.from(context).inflate(R.layout.contact_item, parent, false);
  }

  @Override
  public void bindView(View view, Context context, Cursor cursor) {
    TextView textViewName = view.findViewById(R.id.textViewName);
    TextView textViewEmail = view.findViewById(R.id.textViewEmail);
    TextView textViewOnlyEmail = view.findViewById(R.id.textViewOnlyEmail);
    ImageButton imageButtonDeleteContact = view.findViewById(R.id.imageButtonDeleteContact);

    String name = cursor.getString(cursor.getColumnIndex(ContactsDaoSource.COL_NAME));
    final String email = cursor.getString(cursor.getColumnIndex(ContactsDaoSource.COL_EMAIL));

    if (TextUtils.isEmpty(name)) {
      textViewName.setVisibility(View.GONE);
      textViewEmail.setVisibility(View.GONE);
      textViewOnlyEmail.setVisibility(View.VISIBLE);

      textViewOnlyEmail.setText(email);
      textViewEmail.setText(null);
      textViewName.setText(null);
    } else {
      textViewName.setVisibility(View.VISIBLE);
      textViewEmail.setVisibility(View.VISIBLE);
      textViewOnlyEmail.setVisibility(View.GONE);

      textViewEmail.setText(email);
      textViewName.setText(name);
      textViewOnlyEmail.setText(null);
    }

    if (isDeleteEnabled) {
      imageButtonDeleteContact.setVisibility(View.VISIBLE);
      imageButtonDeleteContact.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View v) {
          if (listener != null) {
            listener.onClick(email);
          }
        }
      });
    } else {
      imageButtonDeleteContact.setVisibility(View.GONE);
    }
  }

  /**
   * This listener can be used to determinate when a contact was deleted.
   */
  public interface OnDeleteContactListener {
    void onClick(String email);
  }
}
