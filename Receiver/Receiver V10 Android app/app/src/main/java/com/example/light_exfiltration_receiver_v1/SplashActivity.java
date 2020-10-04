package com.example.light_exfiltration_receiver_v1;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState);

        Intent i = new Intent(SplashActivity.this, LightExfiltrationReceiverActivity.class);
        startActivity(i);
        finish();
    }
}