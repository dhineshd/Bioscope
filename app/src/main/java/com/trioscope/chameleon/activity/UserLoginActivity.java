package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.support.v7.app.AppCompatActivity;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;

import com.trioscope.chameleon.R;

import lombok.extern.slf4j.Slf4j;

public class UserLoginActivity extends AppCompatActivity {

    private static final String EMPTY_STRING = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_login);

        final EditText editUserNameText = (EditText) findViewById(R.id.editUserNameText);

        final ImageButton continueButton = (ImageButton) findViewById(R.id.enterNameButton);

        editUserNameText.addTextChangedListener(new TextWatcher() {

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                // purposely left unimplemented
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                // purposely left unimplemented
            }

            @Override
            public void afterTextChanged(Editable s) {

                if (s.length() > 0) {
                    continueButton.setVisibility(View.VISIBLE);
                } else {
                    continueButton.setVisibility(View.INVISIBLE);
                }
            }
        });

        editUserNameText.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
                if (actionId == EditorInfo.IME_ACTION_DONE) {
                    continueButton.callOnClick();
                }
                return false;
            }
        });

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!EMPTY_STRING.equals(editUserNameText.getText().toString())) {
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(UserLoginActivity.this);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(getString(R.string.pref_user_name_key), editUserNameText.getText().toString());
                    editor.commit();

                    startActivity(new Intent(UserLoginActivity.this, MainActivity.class));
                    finish();// close this activity; so we can never navigate back here
                    overridePendingTransition(R.anim.slide_in_right, R.anim.slide_out_left);
                }
            }
        });

        if (!EMPTY_STRING.equals(getUserName())) {
            startActivity(new Intent(UserLoginActivity.this, MainActivity.class));
            finish();// close this activity; so we can never navigate back here
        }
    }

    private String getUserName() {
        SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(UserLoginActivity.this);
        return settings.getString(getString(R.string.pref_user_name_key), EMPTY_STRING);
    }
}
