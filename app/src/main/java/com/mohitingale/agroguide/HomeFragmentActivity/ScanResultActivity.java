package com.mohitingale.agroguide.HomeFragmentActivity;

import android.app.ProgressDialog;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import com.bumptech.glide.Glide;
import com.bumptech.glide.request.target.CustomTarget;
import com.bumptech.glide.request.transition.Transition;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.mohitingale.agroguide.R;
import com.mohitingale.agroguide.network.GroqApiClient;

import org.json.JSONObject;

public class ScanResultActivity extends AppCompatActivity {

    // Configure your Groq API Key here: https://console.groq.com/keys
    private static final String GROQ_API_KEY = "";
    
    private String diagnosisJsonContext = "";
    private String currentImageUri = null;
    private MaterialButton btnAskAssistant;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        // Edge-to-Edge
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);

        setContentView(R.layout.activity_scan_result);

        // Set up Toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        toolbar.setNavigationOnClickListener(v -> finish());

        // Load image from intent and start Groq AI analysis
        ImageView ivDiagnosis = findViewById(R.id.iv_diagnosis_image);
        if (ivDiagnosis != null && getIntent().hasExtra("image_uri")) {
            String uriString = getIntent().getStringExtra("image_uri");
            if (uriString != null) {
                Uri imageUri = Uri.parse(uriString);
                
                // Glide Image Loading
                Glide.with(this)
                        .load(imageUri)
                        .centerCrop()
                        .into(ivDiagnosis);

                // Run Vision API diagnosis
                analyzeImageWithGroq(imageUri);
            }
        }

        // Save Report Button Action
        MaterialButton btnSaveReport = findViewById(R.id.btnSaveReport);
        if (btnSaveReport != null) {
            btnSaveReport.setOnClickListener(v -> {
                Toast.makeText(this, "Report saved to profile history", Toast.LENGTH_SHORT).show();
            });
        }

        // Share Report Button Action
        ImageButton btnShareReport = findViewById(R.id.btnShareReport);
        if (btnShareReport != null) {
            btnShareReport.setOnClickListener(v -> {
                if (diagnosisJsonContext.isEmpty()) {
                    Toast.makeText(this, "Analysis incomplete", Toast.LENGTH_SHORT).show();
                    return;
                }
                Intent shareIntent = new Intent(Intent.ACTION_SEND);
                shareIntent.setType("text/plain");
                shareIntent.putExtra(Intent.EXTRA_SUBJECT, "Crop Diagnosis Report");
                shareIntent.putExtra(Intent.EXTRA_TEXT, "AgroGuide AI Crop Diagnosis Report:\n\n" + diagnosisJsonContext);
                startActivity(Intent.createChooser(shareIntent, "Share Report"));
            });
        }

        // Ask AI Assistant Action
        btnAskAssistant = findViewById(R.id.btnAskAssistant);
        if (btnAskAssistant != null) {
            btnAskAssistant.setOnClickListener(v -> {
                if (!diagnosisJsonContext.isEmpty() && currentImageUri != null) {
                    Intent chatbotIntent = new Intent(ScanResultActivity.this, ChatBotActivity.class);
                    chatbotIntent.putExtra("image_uri", currentImageUri);
                    chatbotIntent.putExtra("diagnosis_json", diagnosisJsonContext);
                    startActivity(chatbotIntent);
                } else {
                    Toast.makeText(this, "Wait for diagnosis to complete", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    private void analyzeImageWithGroq(Uri imageUri) {
        this.currentImageUri = imageUri.toString();
        
        // Show loading progress spinner
        View layoutDataCards = findViewById(R.id.layoutDataCards);
        View progressBar = findViewById(R.id.progressBar);
        if (progressBar != null) progressBar.setVisibility(View.VISIBLE);
        if (layoutDataCards != null) layoutDataCards.setVisibility(View.GONE);

        if (GROQ_API_KEY.equals("YOUR_GROQ_API_KEY") || GROQ_API_KEY.isEmpty()) {
            if (progressBar != null) progressBar.setVisibility(View.GONE);
            TextView tvRawOutput = findViewById(R.id.tvRawOutput);
            if (tvRawOutput != null) {
                tvRawOutput.setVisibility(View.VISIBLE);
                tvRawOutput.setText("Groq API Key is not configured. Please open ScanResultActivity.java and configure GROQ_API_KEY to fetch real AI analysis results.");
            }
            Toast.makeText(this, "API Key Required", Toast.LENGTH_LONG).show();
            return;
        }

        // Load bitmap asynchronously using Glide and analyze
        Glide.with(this)
                .asBitmap()
                .load(imageUri)
                .into(new CustomTarget<Bitmap>() {
                    @Override
                    public void onResourceReady(@NonNull Bitmap resource, @Nullable Transition<? super Bitmap> transition) {
                        GroqApiClient apiClient = new GroqApiClient();
                        apiClient.analyzeCrop(resource, GROQ_API_KEY, new GroqApiClient.GroqCallback() {
                            @Override
                            public void onSuccess(String jsonResult) {
                                runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    if (layoutDataCards != null) layoutDataCards.setVisibility(View.VISIBLE);
                                    
                                    diagnosisJsonContext = jsonResult;
                                    try {
                                        // Sanitize potential JSON markdown wrappers
                                        String cleanJson = jsonResult.replace("```json", "").replace("```", "").trim();
                                        JSONObject json = new JSONObject(cleanJson);

                                        // Set Disease Details
                                        TextView tvDiseaseName = findViewById(R.id.tvDiseaseName);
                                        TextView tvScientificName = findViewById(R.id.tvScientificName);
                                        TextView tvConfidence = findViewById(R.id.chipConfidence);
                                        Chip chipRiskLevel = findViewById(R.id.chipRiskLevel);

                                        if (tvDiseaseName != null) tvDiseaseName.setText(json.optString("disease_name", getString(R.string.diagnosis_unknown_disease)));
                                        if (tvScientificName != null) tvScientificName.setText(json.optString("scientific_name", getString(R.string.diagnosis_unknown_scientific_name)));
                                        if (tvConfidence != null) tvConfidence.setText(json.optString("confidence_percentage", getString(R.string.diagnosis_na)));
                                        
                                        if (chipRiskLevel != null) {
                                            String risk = json.optString("risk_level", getString(R.string.diagnosis_unknown_risk));
                                            chipRiskLevel.setText(risk);
                                            if (risk.equalsIgnoreCase("HEALTHY")) {
                                                chipRiskLevel.setChipBackgroundColorResource(R.color.primary_green);
                                            } else if (risk.equalsIgnoreCase("MONITORING")) {
                                                chipRiskLevel.setChipBackgroundColorResource(R.color.warning);
                                            } else {
                                                chipRiskLevel.setChipBackgroundColorResource(R.color.error_red);
                                            }
                                        }

                                        // Set Stats Row (Severity, Spread, Recovery)
                                        TextView tvHealthScore = findViewById(R.id.tvHealthScore);
                                        TextView tvSpread = findViewById(R.id.tvSpread);
                                        TextView tvRecovery = findViewById(R.id.tvRecovery);

                                        if (tvHealthScore != null) tvHealthScore.setText(json.optString("health_score", getString(R.string.diagnosis_na)));
                                        if (tvSpread != null) tvSpread.setText(json.optString("spread_rate", getString(R.string.diagnosis_na)));
                                        if (tvRecovery != null) tvRecovery.setText(json.optString("recovery_time", getString(R.string.diagnosis_na)));

                                        // Set Prevention Tips
                                        TextView tvPreventionTips = findViewById(R.id.tvPreventionTips);
                                        if (tvPreventionTips != null) {
                                            tvPreventionTips.setText(json.optString("prevention_tips", getString(R.string.diagnosis_no_prevention)));
                                        }

                                        // Set Remedies
                                        TextView tvOrganicTitle = findViewById(R.id.tvOrganicTitle);
                                        TextView tvOrganicDesc = findViewById(R.id.tvOrganicDesc);
                                        TextView tvChemicalTitle = findViewById(R.id.tvChemicalTitle);
                                        TextView tvChemicalDesc = findViewById(R.id.tvChemicalDesc);

                                        if (tvOrganicTitle != null) tvOrganicTitle.setText(json.optString("organic_remedy_title", getString(R.string.diagnosis_organic_remedy_fallback)));
                                        if (tvOrganicDesc != null) tvOrganicDesc.setText(json.optString("organic_remedy_desc", getString(R.string.diagnosis_no_organic_remedy)));
                                        if (tvChemicalTitle != null) tvChemicalTitle.setText(json.optString("chemical_remedy_title", getString(R.string.diagnosis_chemical_remedy_fallback)));
                                        if (tvChemicalDesc != null) tvChemicalDesc.setText(json.optString("chemical_remedy_desc", getString(R.string.diagnosis_no_chemical_remedy)));

                                        if (btnAskAssistant != null) {
                                            btnAskAssistant.setVisibility(View.VISIBLE);
                                        }

                                    } catch (Exception e) {
                                        Toast.makeText(ScanResultActivity.this, getString(R.string.diagnosis_failed_parse), Toast.LENGTH_LONG).show();
                                        TextView tvRawOutput = findViewById(R.id.tvRawOutput);
                                        if (tvRawOutput != null) {
                                            tvRawOutput.setVisibility(View.VISIBLE);
                                            tvRawOutput.setText(jsonResult);
                                        }
                                    }
                                });
                            }

                            @Override
                            public void onError(String error) {
                                runOnUiThread(() -> {
                                    if (progressBar != null) progressBar.setVisibility(View.GONE);
                                    Toast.makeText(ScanResultActivity.this, "API Error: " + error, Toast.LENGTH_LONG).show();
                                    TextView tvRawOutput = findViewById(R.id.tvRawOutput);
                                    if (tvRawOutput != null) {
                                        tvRawOutput.setVisibility(View.VISIBLE);
                                        tvRawOutput.setText("Groq Vision API Error:\n" + error);
                                    }
                                });
                            }
                        });
                    }

                    @Override
                    public void onLoadCleared(@Nullable Drawable placeholder) {
                    }
                });
    }
}
