package com.digitalwhy.kasir

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var previewView: PreviewView
    private lateinit var totalTextView: TextView
    private lateinit var btnBayar: MaterialButton
    private lateinit var laserLine: ImageView
    private lateinit var scannerFrame: ImageView
    private lateinit var laserAnimator: ObjectAnimator
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var barcodeScanner: BarcodeScanner
    private lateinit var database: AppDatabase

    private var cameraProvider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var isScanning = true
    private var suppressedBarcode: String? = null
    private val cart = mutableListOf<Product>()

    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                startCamera()
            } else {
                Toast.makeText(this, "Izin kamera dibutuhkan untuk memindai barcode", Toast.LENGTH_LONG).show()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView)
            .isAppearanceLightStatusBars = false
        setContentView(R.layout.activity_main)

        previewView = findViewById(R.id.previewView)
        totalTextView = findViewById(R.id.totalTextView)
        btnBayar = findViewById(R.id.btnBayar)
        laserLine = findViewById(R.id.laserLine)
        scannerFrame = findViewById(R.id.scannerFrame)
        laserAnimator = ObjectAnimator.ofFloat(laserLine, View.TRANSLATION_Y, -180f, 180f).apply {
            duration = 1500L
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
        }
        cameraExecutor = Executors.newSingleThreadExecutor()
        barcodeScanner = BarcodeScanning.getClient()
        database = AppDatabase.getInstance(this)

        findViewById<View>(R.id.menuButton).setOnClickListener {
            Toast.makeText(this, "Menu belum tersedia", Toast.LENGTH_SHORT).show()
        }
        findViewById<View>(R.id.flashButton).setOnClickListener { toggleFlash() }
        findViewById<View>(R.id.flipButton).setOnClickListener { flipCamera() }
        btnBayar.setOnClickListener { checkout() }

        prepopulateDatabase()
        updateTotal()

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
            == PackageManager.PERMISSION_GRANTED
        ) {
            startCamera()
        } else {
            requestPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun prepopulateDatabase() {
        lifecycleScope.launch(Dispatchers.IO) {
            val dao = database.productDao()
            val barcode = "8998866203043"
            if (dao.findByBarcode(barcode) == null) {
                dao.insert(
                    Product(
                        barcode = barcode,
                        name = "Produk Test",
                        price = 5000.0,
                        stock = 10
                    )
                )
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val provider = cameraProviderFuture.get()
            cameraProvider = provider

            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(previewView.surfaceProvider)
            }

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, ::analyzeImage)
                }

            try {
                provider.unbindAll()
                camera = provider.bindToLifecycle(
                    this,
                    CameraSelector.Builder().requireLensFacing(lensFacing).build(),
                    preview,
                    imageAnalysis
                )
                isScanning = true
                startLaserAnimation()
            } catch (e: Exception) {
                Toast.makeText(this, "Gagal membuka kamera: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun pauseCamera() {
        isScanning = false
        cameraProvider?.unbindAll()
        camera = null
    }

    private fun resumeCamera() {
        if (isFinishing || isDestroyed) return
        isScanning = true
        startCamera()
    }

    private fun startLaserAnimation() {
        if (laserAnimator.isPaused) {
            laserAnimator.resume()
        } else if (!laserAnimator.isRunning) {
            laserAnimator.start()
        }
    }

    private fun pauseLaserAnimation() {
        if (laserAnimator.isRunning) {
            laserAnimator.pause()
        }
    }

    private fun toggleFlash() {
        val currentCamera = camera ?: return
        if (!currentCamera.cameraInfo.hasFlashUnit()) {
            Toast.makeText(this, "Perangkat tidak memiliki flash", Toast.LENGTH_SHORT).show()
            return
        }
        val isOn = currentCamera.cameraInfo.torchState.value == TorchState.ON
        currentCamera.cameraControl.enableTorch(!isOn)
    }

    private fun flipCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        if (isScanning) startCamera()
    }

    @androidx.camera.core.ExperimentalGetImage
    private fun analyzeImage(imageProxy: ImageProxy) {
        if (!isScanning) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage == null) {
            imageProxy.close()
            return
        }

        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
        barcodeScanner.process(image)
            .addOnSuccessListener { barcodes ->
                val value = barcodes.firstOrNull()?.rawValue
                if (value == null) {
                    suppressedBarcode = null
                } else if (value != suppressedBarcode) {
                    suppressedBarcode = null
                    handleBarcode(value)
                }
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    }

    private fun handleBarcode(barcode: String) {
        if (!isScanning) return
        vibrateOnScan()
        runOnUiThread {
            Toast.makeText(this, "Scan Berhasil", Toast.LENGTH_SHORT).show()
            scannerFrame.setImageResource(R.drawable.scanner_frame_green)
        }

        suppressedBarcode = barcode
        pauseCamera()

        lifecycleScope.launch {
            val product = database.productDao().findByBarcode(barcode)
            showProductBottomSheet(barcode, product)
        }
    }

    private fun showProductBottomSheet(barcode: String, product: Product?) {
        if (isFinishing || isDestroyed) return

        pauseLaserAnimation()

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_product, null)
        dialog.setContentView(view)

        val nameTextView = view.findViewById<TextView>(R.id.productNameTextView)
        val barcodeTextView = view.findViewById<TextView>(R.id.barcodeTextView)
        val priceTextView = view.findViewById<TextView>(R.id.priceTextView)
        val addToCartButton = view.findViewById<MaterialButton>(R.id.addToCartButton)

        barcodeTextView.text = barcode

        if (product == null) {
            nameTextView.text = "Produk Tidak Ditemukan"
            priceTextView.visibility = View.GONE
            addToCartButton.text = "TUTUP"
            addToCartButton.backgroundTintList =
                ColorStateList.valueOf(Color.parseColor("#9E9E9E"))
            addToCartButton.setOnClickListener { dialog.dismiss() }
        } else {
            nameTextView.text = product.name
            priceTextView.text = formatCurrency(product.price)
            addToCartButton.setOnClickListener {
                cart.add(product)
                updateTotal()
                dialog.dismiss()
            }
        }

        dialog.setOnShowListener {
            dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
                ?.setBackgroundColor(Color.TRANSPARENT)
        }
        dialog.setOnDismissListener {
            scannerFrame.setImageResource(R.drawable.scanner_frame)
            startLaserAnimation()
            resumeCamera()
        }
        dialog.show()
    }

    private fun vibrateOnScan() {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vibratorManager =
                getSystemService(VibratorManager::class.java) as? VibratorManager
            vibratorManager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(VIBRATOR_SERVICE) as? Vibrator
        } ?: return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator.vibrate(VibrationEffect.createOneShot(100L, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            vibrator.vibrate(100L)
        }
    }

    private fun checkout() {
        if (cart.isEmpty()) return

        val items = cart.toList()
        val total = items.sumOf { it.price }
        val itemsSummary = items.groupBy { it.id }.values.joinToString(", ") { group ->
            "${group.first().name} x${group.size}"
        }

        btnBayar.isEnabled = false
        btnBayar.alpha = 0.5f

        lifecycleScope.launch(Dispatchers.IO) {
            items.groupBy { it.id }.forEach { (_, group) ->
                val product = group.first()
                val newStock = (product.stock - group.size).coerceAtLeast(0)
                database.productDao().updateProduct(product.copy(stock = newStock))
            }

            database.transactionDao().insert(
                Transaction(
                    timestamp = System.currentTimeMillis(),
                    totalAmount = total,
                    itemsSummary = itemsSummary
                )
            )

            runOnUiThread {
                cart.clear()
                updateTotal()
                Toast.makeText(this@MainActivity, "Pembayaran Berhasil!", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun updateTotal() {
        val total = cart.sumOf { it.price }
        totalTextView.text = formatCurrency(total)

        if (total <= 0.0) {
            btnBayar.isEnabled = false
            btnBayar.alpha = 0.5f
        } else {
            btnBayar.isEnabled = true
            btnBayar.alpha = 1f
            btnBayar.setBackgroundResource(R.drawable.button_gradient)
        }
    }

    private fun formatCurrency(value: Double): String {
        val formatter = NumberFormat.getNumberInstance(Locale("id", "ID")).apply {
            minimumFractionDigits = 0
            maximumFractionDigits = 2
        }
        return "Rp ${formatter.format(value)}"
    }

    override fun onDestroy() {
        super.onDestroy()
        isScanning = false
        laserAnimator.cancel()
        cameraProvider?.unbindAll()
        cameraExecutor.shutdown()
        barcodeScanner.close()
    }
}
