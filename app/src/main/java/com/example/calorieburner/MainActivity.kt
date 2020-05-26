package com.example.calorieburner

import android.Manifest.permission.CAMERA
import android.Manifest.permission.READ_EXTERNAL_STORAGE
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.Keep
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import clarifai2.api.ClarifaiBuilder
import clarifai2.api.ClarifaiClient
import clarifai2.dto.input.ClarifaiInput
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSet
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.result.DataReadResponse
import com.google.android.gms.tasks.Task
import kotlinx.android.synthetic.main.activity_main.*
import okhttp3.*
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.text.DateFormat
import java.util.*
import java.util.concurrent.TimeUnit

const val TAG = "BasicHistoryApi"

/**
 * This enum is used to define actions that can be performed after a successful sign in to Fit.
 * One of these values is passed to the Fit sign-in, and returned in a successful callback, allowing
 * subsequent execution of the desired action.
 */
enum class FitActionRequestCode {
    INSERT_AND_READ_DATA/*,
    UPDATE_AND_READ_DATA,
    DELETE_DATA*/
}

class MainActivity : AppCompatActivity() {
    private val RECIPS_API_KEY = "fc20d79115ef44aa9fd3efa3d8535de0"
    private val IMAGE_RECONIZE_KEY = "d6b3abde50a045069056dd9327f476ad"

    val FILE_NAME = "temp.jpg"
    val CAMERA_IMAGE_REQUEST = 3
    val CAMERA_PERMISSIONS_REQUEST = 2

    var walkingTime = 0
    var bikingTime = 0
    var runningTime = 0

    var walkingCaloriesPerHour = -1
    var bikingCaloriesPerHour = -1
    var runningCaloriesPerHour = -1

    var walkingCaloriesPerHourDefault = 221
    var bikingCaloriesPerHourDefault = 625
    var runningCaloriesPerHourDefault = 588

    private val client = OkHttpClient()

    private val clarifaiClient : ClarifaiClient = ClarifaiBuilder("d6b3abde50a045069056dd9327f476ad").buildSync()

    private var mCurrentPhotoPath: String? = null

    private val dateFormat = DateFormat.getDateInstance()
    private val fitnessOptions: FitnessOptions by lazy {
        FitnessOptions.builder()
            .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
            .addDataType(DataType.AGGREGATE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_WRITE)
            .build()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        //initializeLogging()

        fitSignIn(FitActionRequestCode.INSERT_AND_READ_DATA)

        buttonSearchRecipe.setOnClickListener {
            searchRecipe()
        }

        buttonSearch.setOnClickListener {
            searchFood(foodText.text.toString())
        }

        buttonCamera.setOnClickListener {
            openCamera()
        }
    }

    private fun openCamera () {
        if (requestPermission(this, CAMERA_PERMISSIONS_REQUEST, READ_EXTERNAL_STORAGE, CAMERA)){
            val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
            val photoUri = FileProvider.getUriForFile(
                this,
                applicationContext.packageName.toString() + ".provider",
                getCameraFile()
            )
            intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            startActivityForResult(intent, CAMERA_IMAGE_REQUEST)
        }
    }

    fun getCameraFile(): File {
        val dir = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File(dir, FILE_NAME)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == CAMERA_IMAGE_REQUEST && resultCode == RESULT_OK) {
            val photoUri = FileProvider.getUriForFile(
                this,
                applicationContext.packageName.toString() + ".provider",
                getCameraFile()
            )

            /*val requestBody = MultipartBody.Builder().setType(MultipartBody.FORM)
                .addFormDataPart("file", getCameraFile().name,
                    RequestBody.create(MediaType.parse("image/${getCameraFile().extension}"), getCameraFile()))
                .build()

            // classify and analyze
            val request = Request.Builder()
                .url("https://api.spoonacular.com/food/images/analyze?apiKey=${RECIPS_API_KEY}")
                .post(requestBody)
                .build()

            client.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, e: IOException) {
                    TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                }

                override fun onResponse(call: Call, response: Response) {
                    val responseJson = JSONObject(response.body()!!.string())
                    Log.i("ResponseAPIrecipes", responseJson.toString())
                }
            })*/

            Thread(Runnable {
                val imageStream = contentResolver.openInputStream(photoUri);
                val imageByteArray = imageStream?.readBytes()
                val foodModel = clarifaiClient.defaultModels.foodModel()
                val request = foodModel.predict().withInputs(imageByteArray?.let {
                    ClarifaiInput.forImage(
                        it
                    )
                })
                val result = request.executeSync()
                val resultList = result.get()
                Log.i("Clarifai", resultList[0].data()[0].name().toString())
                searchFood(resultList[0].data()[0].name().toString())

            }).start()
        }
    }

    fun requestPermission(activity: Activity, requestCode: Int, vararg permissions: String): Boolean {
        var granted = true
        val permissionsNeeded =
            ArrayList<String>()
        for (s in permissions) {
            val permissionCheck: Int = ContextCompat.checkSelfPermission(activity, s)
            val hasPermission =
                permissionCheck == PackageManager.PERMISSION_GRANTED
            granted = granted and hasPermission
            if (!hasPermission) {
                permissionsNeeded.add(s)
            }
        }
        return if (granted) {
            true
        } else {
            ActivityCompat.requestPermissions(
                activity,
                permissionsNeeded.toTypedArray(),
                requestCode
            )
            false
        }
    }

    fun permissionGranted(requestCode: Int, permissionCode: Int, grantResults: IntArray): Boolean {
        return if (requestCode == permissionCode) {
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        } else false
    }


    private fun searchFood (foodText : String) {


        val url = "https://api.spoonacular.com/food/products/search?apiKey=${RECIPS_API_KEY}&query=$foodText"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onResponse(call: Call, response: Response) {
                // RESPONSE BODY CAN ONLY BE USED ONE TIME
                // https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/#the-response-body-can-be-consumed-only-once
                //Log.i("RequestAPIrecipes", response.body()!!.string())
                val responseJson = JSONObject(response.body()!!.string())
                Log.i("ResponseAPIrecipes", responseJson.toString())
                val results = responseJson.getJSONArray("products")
                Log.i("ResponseAPIrecipes", results.toString())
                val firstResultId = results.getJSONObject(0).getInt("id")
                Log.i("ResponseAPIrecipes", firstResultId.toString())

                val urlGetInfo = "https://api.spoonacular.com/food/products/${firstResultId}/?apiKey=${RECIPS_API_KEY}"

                val requestGetInfo = Request.Builder()
                    .url(urlGetInfo)
                    .build()
                client.newCall(requestGetInfo).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseJsonInfo = JSONObject(response.body()!!.string())
                        Log.i("ResponseAPIrecipes", responseJsonInfo.toString())
                        val title = responseJsonInfo.getString("title")
                        val nutrients = responseJsonInfo.getJSONObject("nutrition").getJSONArray("nutrients")
                        var calories : Double = - 1.0
                        for (i in 0 until nutrients.length()) {
                            val nutrient = nutrients.getJSONObject(i)
                            Log.i("ResponseAPIrecipes", nutrient.toString())
                            if (nutrient.getString("title") == "Calories") {
                                calories = nutrient.getInt("amount").toDouble()
                                break
                            }
                        }
                        Log.i("ResponseAPIrecipes", "calories")
                        Log.i("ResponseAPIrecipes", calories.toString())
                        runOnUiThread {
                            if (calories == -1.0) {
                                calorieTextView.text = "We couldn't find the number of calories for that food"
                            } else {
                                when (maxOf(runningTime, walkingTime, bikingTime)) {
                                    runningTime -> {
                                        val timeToBurn : Double = if (runningCaloriesPerHour != -1) {
                                            calories.div(runningCaloriesPerHour)
                                        } else {
                                            calories.div(runningCaloriesPerHourDefault)
                                        }
                                        calorieTextView.text = "Burn those ${calories} calories in ${timeToBurn} hours of the ${title} with a Run"
                                    }
                                    bikingTime -> {
                                        val timeToBurn : Double = if (bikingCaloriesPerHour != -1) {
                                            calories.div(bikingCaloriesPerHour)
                                        } else {
                                            calories.div(bikingCaloriesPerHourDefault)
                                        }
                                        calorieTextView.text = "Burn those ${calories} calories in ${timeToBurn} hours of the ${title} with a Bike Ride"
                                    }
                                    walkingTime -> {
                                        val timeToBurn : Double = if (walkingCaloriesPerHour != -1) {
                                            calories.div(walkingCaloriesPerHour)
                                        } else {
                                            calories.div(walkingCaloriesPerHourDefault)
                                        }
                                        calorieTextView.text = "Burn those ${calories} calories in ${timeToBurn} hours of the ${title} with a Walk"
                                    }
                                }
                            }
                        }
                    }
                })
            }
        })
    }

    private fun searchRecipe () {
        val textToSearch = recipeText.text

        val url = "https://api.spoonacular.com/recipes/search?apiKey=${RECIPS_API_KEY}&query=$textToSearch"

        val request = Request.Builder()
            .url(url)
            .build()

        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
            }

            override fun onResponse(call: Call, response: Response) {
                // RESPONSE BODY CAN ONLY BE USED ONE TIME
                // https://square.github.io/okhttp/4.x/okhttp/okhttp3/-response-body/#the-response-body-can-be-consumed-only-once
                //Log.i("RequestAPIrecipes", response.body()!!.string())
                val responseJson = JSONObject(response.body()!!.string())
                val results = responseJson.getJSONArray("results")
                Log.i("ResponseAPIrecipes", results.toString())
                val title = results.getJSONObject(0).getString("title")
                val firstResultId = results.getJSONObject(0).getInt("id")
                Log.i("ResponseAPIrecipes", firstResultId.toString())

                val urlGetInfo = "https://api.spoonacular.com/recipes/${firstResultId}/nutritionWidget.json?apiKey=${RECIPS_API_KEY}"

                val requestGetInfo = Request.Builder()
                    .url(urlGetInfo)
                    .build()
                client.newCall(requestGetInfo).enqueue(object : Callback {
                    override fun onFailure(call: Call, e: IOException) {
                        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
                    }

                    override fun onResponse(call: Call, response: Response) {
                        val responseJsonInfo = JSONObject(response.body()!!.string())
                        Log.i("ResponseAPIrecipes", responseJsonInfo.toString())
                        val calories = responseJsonInfo.getInt("calories").toDouble()

                        Log.i("ResponseAPIrecipes", calories.toString())
                        runOnUiThread {
                            if (calories == -1.0) {
                                calorieTextView.text = "We couldn't find the number of calories for that food"
                            } else {
                                when (maxOf(runningTime, walkingTime, bikingTime)) {
                                    runningTime -> {
                                        val timeToBurn : Double = if (runningCaloriesPerHour != -1) {
                                            calories.div(runningCaloriesPerHour)
                                        } else {
                                            calories.div(runningCaloriesPerHourDefault)
                                        }
                                        calorieTextView.text = "Burn those ${calories} calories in ${timeToBurn} hours of the ${title} with a Run"
                                    }
                                    bikingTime -> {
                                        val timeToBurn : Double = if (bikingCaloriesPerHour != -1) {
                                            calories.div(bikingCaloriesPerHour)
                                        } else {
                                            calories.div(bikingCaloriesPerHourDefault)
                                        }
                                        calorieTextView.text = "Burn those ${calories} calories in ${timeToBurn} hours of the ${title} with a Bike Ride"
                                    }
                                    walkingTime -> {
                                        val timeToBurn : Double = if (walkingCaloriesPerHour != -1) {
                                            calories.div(walkingCaloriesPerHour)
                                        } else {
                                            calories.div(walkingCaloriesPerHourDefault)
                                        }
                                        calorieTextView.text = "Burn those ${calories} calories in ${timeToBurn} hours of the ${title} with a Walk"
                                    }
                                }
                            }
                        }
                    }
                })
            }
        })
    }

    /**
     * Checks that the user is signed in, and if so, executes the specified function. If the user is
     * not signed in, initiates the sign in flow, specifying the post-sign in function to execute.
     *
     * @param requestCode The request code corresponding to the action to perform after sign in.
     */
    private fun fitSignIn(requestCode: FitActionRequestCode) {
        if (oAuthPermissionsApproved()) {
            performActionForRequestCode(requestCode)
            performActionTest()
        } else {
            requestCode.let {
                GoogleSignIn.requestPermissions(
                    this,
                    requestCode.ordinal,
                    getGoogleAccount(), fitnessOptions)
            }
        }
    }

    /**
     * Runs the desired method, based on the specified request code. The request code is typically
     * passed to the Fit sign-in flow, and returned with the success callback. This allows the
     * caller to specify which method, post-sign-in, should be called.
     *
     * @param requestCode The code corresponding to the action to perform.
     */
    private fun performActionForRequestCode(requestCode: FitActionRequestCode) = when (requestCode) {
        FitActionRequestCode.INSERT_AND_READ_DATA -> readHistoryData()
    }

    private fun performActionTest () {
        val readRequest = queryFitnessData()
        Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(readRequest)
            .addOnSuccessListener { dataReadResponse ->
                printDataTest(dataReadResponse)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "There was a problem reading the data.", e)
            }
    }

    private fun oAuthPermissionsApproved() = GoogleSignIn.hasPermissions(getGoogleAccount(), fitnessOptions)

    /**
     * Gets a Google account for use in creating the Fitness client. This is achieved by either
     * using the last signed-in account, or if necessary, prompting the user to sign in.
     * `getAccountForExtension` is recommended over `getLastSignedInAccount` as the latter can
     * return `null` if there has been no sign in before.
     */
    private fun getGoogleAccount() = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

    private fun readHistoryData(): Task<DataReadResponse> {
        Log.i(TAG, "Start ReadHistoryData: ")// Begin by creating the query.
        val readRequestCyclcing = queryFitnessDataSummary()

        return Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(readRequestCyclcing)
            .addOnSuccessListener { dataReadResponse ->
                printData(dataReadResponse)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "There was a problem reading the data.", e)
            }

        // Invoke the History API to fetch the data with the query
        /*return Fitness.getHistoryClient(this, getGoogleAccount())
            .readData(readRequest)
            .addOnSuccessListener { dataReadResponse ->
                printData(dataReadResponse)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "There was a problem reading the data.", e)
            } */
    }

    /** Returns a [DataReadRequest] for all step count changes in the past month.  */
    private fun queryFitnessData(): DataReadRequest {
        Log.i(TAG, "Start queryFitnessData: ")
        // [START build_read_data_request]
        // Setting a start and end date using a range of 1 week before this moment.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -1)
        val startTime = calendar.timeInMillis

        Log.i(TAG, "Range Start: ${dateFormat.format(startTime)}")
        Log.i(TAG, "Range End: ${dateFormat.format(endTime)}")

        return DataReadRequest.Builder()
            .aggregate(DataType.TYPE_BASAL_METABOLIC_RATE, DataType.AGGREGATE_BASAL_METABOLIC_RATE_SUMMARY)
            .bucketByActivityType(30, TimeUnit.MINUTES)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()
    }

    /** Returns a [DataReadRequest] for all step count changes in the past month.  */
    private fun queryFitnessDataSummary(): DataReadRequest {
        Log.i(TAG, "Start queryFitnessDataSummary: ")
        // [START build_read_data_request]
        // Setting a start and end date using a range of 1 week before this moment.
        val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
        val now = Date()
        calendar.time = now
        val endTime = calendar.timeInMillis
        calendar.add(Calendar.MONTH, -1)
        val startTime = calendar.timeInMillis

        Log.i(TAG, "Range Start: ${dateFormat.format(startTime)}")
        Log.i(TAG, "Range End: ${dateFormat.format(endTime)}")

        return DataReadRequest.Builder()
            .aggregate(DataType.TYPE_ACTIVITY_SEGMENT, DataType.AGGREGATE_ACTIVITY_SUMMARY)
            .bucketByActivityType(30, TimeUnit.MINUTES)
            .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
            .build()

    }

    /**
     * Logs a record of the query result. It's possible to get more constrained data sets by
     * specifying a data source or data type, but for demonstrative purposes here's how one would
     * dump all the data. In this sample, logging also prints to the device screen, so we can see
     * what the query returns, but your app should not log fitness information as a privacy
     * consideration. A better option would be to dump the data you receive to a local data
     * directory to avoid exposing it to other applications.
     */
    private fun printData(dataReadResult: DataReadResponse) {
        Log.i(TAG, "Start printData: ")
        // [START parse_read_data_result]
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.buckets.isNotEmpty()) {
            Log.i(TAG, "Number of returned buckets of DataSets is: " + dataReadResult.buckets.size)
            for (bucket in dataReadResult.buckets) {
                bucket.dataSets.forEach { dumpDataSet(it) }
            }
        } else if (dataReadResult.dataSets.isNotEmpty()) {
            Log.i(TAG, "Number of returned DataSets is: " + dataReadResult.dataSets.size)
            dataReadResult.dataSets.forEach { dumpDataSet(it) }
        }
        // [END parse_read_data_result]
    }

    private fun printDataTest(dataReadResult: DataReadResponse) {
        Log.i(TAG, "Start printData: ")
        // [START parse_read_data_result]
        // If the DataReadRequest object specified aggregated data, dataReadResult will be returned
        // as buckets containing DataSets, instead of just DataSets.
        if (dataReadResult.buckets.isNotEmpty()) {
            Log.i(TAG, "Number of returned buckets of DataSets is: " + dataReadResult.buckets.size)
            for (bucket in dataReadResult.buckets) {
                Log.i(TAG, "bucket activity: ${bucket.bucketType}")
                bucket.dataSets.forEach {
                    Log.i(TAG, "dataPoint size: ${it.dataPoints.size}")
                    it.dataPoints.forEach { dp ->
                        Log.i(TAG, "field: ${dp.getValue(Field.FIELD_AVERAGE)}")
                        when (bucket.activity) {
                            "walking" -> walkingCaloriesPerHour = (dp.getValue(Field.FIELD_AVERAGE).asInt().div(24))
                            "running" -> runningCaloriesPerHour = (dp.getValue(Field.FIELD_AVERAGE).asInt().div(24))
                            "biking" -> bikingCaloriesPerHour = (dp.getValue(Field.FIELD_AVERAGE).asInt().div(24))
                        }
                    }
                    //Log.i(TAG, "bucket activity: ${it.dataType.fields[0].name}")
                    //Log.i(TAG, "bucket activity: ${it.dataType.fields[0]}")
                }
            }
        } else if (dataReadResult.dataSets.isNotEmpty()) {
            Log.i(TAG, "Number of returned DataSets is: " + dataReadResult.dataSets.size)
            //dataReadResult.dataSets.forEach { dumpDataSet(it) }
        }
        // [END parse_read_data_result]
    }

    // [START parse_dataset]
    private fun dumpDataSet(dataSet: DataSet) {
        Log.i(TAG, "Data returned for Data type: ${dataSet.dataType.name}")

        for (dp in dataSet.dataPoints) {
            Log.i(TAG, "Data point:")
            Log.i(TAG, "\tType: ${dp.dataType.name}")
            //Log.i(TAG, "\tStart: ${dp.getStartTimeString()}")
            //Log.i(TAG, "\tEnd: ${dp.getEndTimeString()}")
            Log.i(TAG, "\tFields: ${dp.dataType.fields}")
            var walkingField = false
            var runningField = false
            var bikingFiled = false
            dp.dataType.fields.forEach {
                if (it.name == "activity") {
                    Log.i(TAG, "\tField: ${it.name} Value: ${dp.getValue(it).asActivity()}")
                    when (dp.getValue(it).toString().toInt()) {
                        7 -> walkingField = true
                        8 -> runningField = true
                        1 -> bikingFiled = true
                    }
                }
                if (it.name == "duration") {
                    if (walkingField) {
                        walkingTime = dp.getValue(it).toString().toInt()
                        walkingField = false
                    }
                    if (runningField) {
                        runningTime = dp.getValue(it).toString().toInt()
                        runningField = false
                    }
                    if (bikingFiled) {
                        bikingTime = dp.getValue(it).toString().toInt()
                        bikingFiled = false
                    }
                }
                Log.i(TAG, "\tField: ${it.name} Value: ${dp.getValue(it)}")
            }
        }
    }
    // [END parse_dataset]

}
