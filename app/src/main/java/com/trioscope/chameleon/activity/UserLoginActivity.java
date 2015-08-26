package com.trioscope.chameleon.activity;

import android.content.Intent;
import android.content.SharedPreferences;
import android.media.Image;
import android.preference.PreferenceManager;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageButton;

import com.trioscope.chameleon.R;

public class UserLoginActivity extends EnableForegroundDispatchForNFCMessageActivity {

    private static final String EMPTY_STRING = "";
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_user_login);

        final EditText editUserNameText = (EditText) findViewById(R.id.editUserNameText);

        final ImageButton continueButton = (ImageButton) findViewById(R.id.enterNameButton);

        continueButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                if(!EMPTY_STRING.equals(editUserNameText.getText().toString())) {
                    SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(UserLoginActivity.this);
                    SharedPreferences.Editor editor = settings.edit();
                    editor.putString(getString(R.string.pref_user_name_key), editUserNameText.getText().toString());
                    editor.commit();

                    startActivity(new Intent(UserLoginActivity.this, MainActivity.class));
                }
            }
        });

        if(!EMPTY_STRING.equals(getUserName())) {
            startActivity(new Intent(UserLoginActivity.this, MainActivity.class));
        }
    }

     private String getUserName() {
         SharedPreferences settings = PreferenceManager.getDefaultSharedPreferences(UserLoginActivity.this);
         return settings.getString(getString(R.string.pref_user_name_key), EMPTY_STRING);
     }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_user_login, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
