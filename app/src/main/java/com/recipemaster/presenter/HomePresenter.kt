package com.recipemaster.presenter

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
import androidx.core.content.ContextCompat
import com.facebook.AccessToken
import com.facebook.CallbackManager
import com.facebook.FacebookCallback
import com.facebook.FacebookException
import com.facebook.login.LoginManager
import com.facebook.login.LoginResult
import com.karumi.dexter.Dexter
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionDeniedResponse
import com.karumi.dexter.listener.PermissionGrantedResponse
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.single.PermissionListener
import com.recipemaster.contract.HomeContract
import com.recipemaster.model.json.JsonParser
import com.recipemaster.model.repository.shared_preferences.SharedPreferencesManager
import com.recipemaster.model.network.request.user.UserClient
import com.recipemaster.util.MessageCallback
import com.recipemaster.util.Permissions
import com.recipemaster.view.RecipeDetailsActivity
import org.json.JSONObject


class HomePresenter(
    _view: HomeContract.View?,
    _client: UserClient
) : HomeContract.Presenter {

    private var view: HomeContract.View? = _view
    private val client: UserClient = _client

    private lateinit var callbackManager: CallbackManager


    init {
        view?.setOnClickListeners()
        view?.setGetTheRecipeButtonToNotEnabled()

    }

    override fun dropView() {
        view = null
    }

    override fun openRecipeDetailsActivity() {
        if (SharedPreferencesManager.isLoggedIn()) {
            val intent = Intent(view?.getContext(), RecipeDetailsActivity::class.java)
            view?.getContext()?.startActivity(intent)
        } else {
            view?.showToast(MessageCallback.NOT_LOGGED)
        }

    }

    override fun tryLoginToFacebook() {
        if (ContextCompat.checkSelfPermission(
                view!!.getContext(),
                Manifest.permission.RECORD_AUDIO
            )
            == PackageManager.PERMISSION_GRANTED
        ) {
            logIntoFacebook()
        } else {
            requestAudioPermissions()
            logIntoFacebook()
        }
    }

    override fun requestAudioPermissions() {
        Dexter.withContext(view?.getContext())
            .withPermission(Manifest.permission.RECORD_AUDIO)
            .withListener(object : PermissionListener {
                override fun onPermissionGranted(response: PermissionGrantedResponse) {
                    logIntoFacebook()
                }

                override fun onPermissionDenied(response: PermissionDeniedResponse) {
                    view?.showToast(MessageCallback.PERMISSION_DENIED)
                }

                override fun onPermissionRationaleShouldBeShown(
                    permission: PermissionRequest?,
                    token: PermissionToken?
                ) {
                    view?.showToast(MessageCallback.PERMISSION_RATIONALE)
                }
            }).check()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        callbackManager.onActivityResult(requestCode, resultCode, data)
        if (callbackManager.onActivityResult(requestCode, resultCode, data)) {
            return
        }
    }

    override fun logIntoFacebook() {
        if (SharedPreferencesManager.isLoggedIn()) {
            view?.showToast(MessageCallback.ALREADY_LOGGED)

        } else {
            callbackManager = CallbackManager.Factory.create()
            val loginManager = LoginManager.getInstance()

            loginManager.logInWithReadPermissions(
                view as Activity,
                Permissions.facebookCallPermission
            )

            LoginManager.getInstance().registerCallback(callbackManager,
                object : FacebookCallback<LoginResult?> {

                    override fun onSuccess(loginResult: LoginResult?) {
                        onSuccessFacebookCallback()
                    }

                    override fun onCancel() {
                        onCanceledFacebookCallback()
                    }

                    override fun onError(error: FacebookException?) {
                        onErrorFacebookCallback()
                    }
                })
        }

    }

    override fun onSuccessFacebookCallback() {
        if (AccessToken.getCurrentAccessToken() == null) {
            view?.setGetTheRecipeButtonToNotEnabled()
            view?.showToast(MessageCallback.NO_DATA_RECEIVED)
        } else {
            client.requestUserData(userDataCallback)
            view?.setGetTheRecipeButtonToEnabled()
            view?.showToast(MessageCallback.LOGIN_SUCCESS)
        }
    }

    override fun onCanceledFacebookCallback() {
        if (AccessToken.getCurrentAccessToken() == null) {
            view?.setGetTheRecipeButtonToNotEnabled()
            view?.showToast(MessageCallback.LOGIN_CANCELED)
        } else {
            client.requestUserData(userDataCallback)
            view?.setGetTheRecipeButtonToEnabled()
            view?.showToast(MessageCallback.LOGIN_SUCCESS)
        }
    }

    override fun onErrorFacebookCallback() {
        view?.setGetTheRecipeButtonToNotEnabled()
        view?.showToast(MessageCallback.LOGIN_ERROR)
    }


    private var userDataCallback = object : HomeContract.OnResponseCallback {
        override fun onResponse(json: JSONObject?) {
            parseJsonResponse(json)
            Log.d("FB_RESPONSE: ", json.toString())
        }
    }

    override fun parseJsonResponse(json: JSONObject?) {
        val jsonParser = JsonParser(json)
        val userName = jsonParser.parseName()
        val userId = jsonParser.parseUserId()
        val profilePictureUrl = jsonParser.createProfilePictureUrl()
        saveDataIntoSharedPreferences(userId, userName, profilePictureUrl)


    }

    override fun saveDataIntoSharedPreferences(
        userId: String?,
        userName: String?,
        profilePictureUrl: String?
    ) {
        SharedPreferencesManager.setCurrentUserId(userId)
        SharedPreferencesManager.setCurrentUserName(userName)
        SharedPreferencesManager.setCurrentUserPhotoUrl(profilePictureUrl)
    }

}