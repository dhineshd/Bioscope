package com.trioscope.chameleon.activity;

import android.content.Context;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.trioscope.chameleon.R;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;

public class TermsofuseActivity extends EnableForegroundDispatchForNFCMessageActivity {

    private TextView termsOfUseTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_termsofuse);

        termsOfUseTextView = (TextView) findViewById(R.id.terms_of_use_text_view);

        String data = readTextFile(this, R.raw.terms_of_use);
        termsOfUseTextView.setMovementMethod(new ScrollingMovementMethod());
        termsOfUseTextView.setText(data);

    }


    @Override
    protected void onResume() {
        super.onResume();


    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_termsofuse, menu);
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

    private static String readTextFile(Context ctx, int resId) {
        InputStream inputStream = ctx.getResources().openRawResource(resId);

        InputStreamReader inputreader = new InputStreamReader(inputStream);
        BufferedReader bufferedreader = new BufferedReader(inputreader);
        String line;
        StringBuilder stringBuilder = new StringBuilder();
        try {
            while ((line = bufferedreader.readLine()) != null) {
                stringBuilder.append(line);
                stringBuilder.append('\n');
            }
        } catch (IOException e) {
            return null;
        }
        return stringBuilder.toString();
    }
}
