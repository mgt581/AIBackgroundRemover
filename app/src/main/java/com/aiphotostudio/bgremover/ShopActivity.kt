package com.aiphotostudio.bgremover

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.aiphotostudio.bgremover.databinding.ActivityShopBinding
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.Purchase

class ShopActivity : AppCompatActivity(), BillingManager.BillingListener {
    private lateinit var binding: ActivityShopBinding
    private lateinit var billingManager: BillingManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityShopBinding.inflate(layoutInflater)
        setContentView(binding.root)

        billingManager = BillingManager(this)
        billingManager.listener = this

        setupClickListeners()
    }

    private fun setupClickListeners() {
        binding.btnBack.setOnClickListener { finish() }

        binding.btnSupport?.setOnClickListener {
            val url = "https://wa.me/447843969254"
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            startActivity(intent)
        }

        binding.cardMonthly.setOnClickListener {
            // Using the monthly base plan 'yearly' inside the 'aips_yearly_pro' product
            billingManager.launchPurchaseFlow("aips_yearly_pro", BillingClient.ProductType.SUBS, "yearly")
        }

        binding.cardYearly.setOnClickListener {
            // Using the yearly base plan 'yearlyplan' with the 30-day trial 'trial30'
            billingManager.launchPurchaseFlow("aips_yearly_pro", BillingClient.ProductType.SUBS, "yearlyplan", "trial30")
        }

        binding.cardCredits10.setOnClickListener {
            billingManager.launchPurchaseFlow("credits_10", BillingClient.ProductType.INAPP)
        }

        binding.cardCredits25.setOnClickListener {
            billingManager.launchPurchaseFlow("credits_25", BillingClient.ProductType.INAPP)
        }

        binding.cardCredits50.setOnClickListener {
            billingManager.launchPurchaseFlow("credits_50", BillingClient.ProductType.INAPP)
        }
    }

    override fun onBillingSetupFinished() {
        // Ready to make purchases
    }

    override fun onPurchaseSuccess(purchase: Purchase) {
        Toast.makeText(this, "Purchase successful!", Toast.LENGTH_SHORT).show()
        finish()
    }

    override fun onPurchaseFailure(error: String) {
        Toast.makeText(this, "Purchase failed: $error", Toast.LENGTH_LONG).show()
    }
}
