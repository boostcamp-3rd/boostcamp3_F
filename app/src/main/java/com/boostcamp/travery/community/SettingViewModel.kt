package com.boostcamp.travery.community

import android.app.Application
import com.boostcamp.travery.base.BaseViewModel
import androidx.lifecycle.MutableLiveData
import android.content.Context.MODE_PRIVATE
import androidx.databinding.ObservableBoolean
import androidx.databinding.ObservableField
import com.boostcamp.travery.Constants


class SettingViewModel(application: Application) : BaseViewModel(application) {

    val appVersion = MutableLiveData<String>()
    val userName = ObservableField<String>()
    val autoUploadState = ObservableBoolean(false)
    val loginBtnString = ObservableField<String>()

    val openLogin = MutableLiveData<Boolean>()

    init {
        getPreferences()
        appVersion.value = application.packageManager.getPackageInfo(application.packageName, 0).versionName
    }

    fun logout() {
        if (loginBtnString.get() == "로그인") {
            openLogin.value = true
        } else {
            removePreferences()
            userName.set("로그인을 해주세요.")
            loginBtnString.set("로그인")
        }
    }

    fun onCheckedChanged(boolean: Boolean) {
        savePreferences(boolean)
    }

    // 값 불러오기
    fun getPreferences() {
        val pref = getApplication<Application>().getSharedPreferences(Constants.PREF_NAME_LOGIN, MODE_PRIVATE)
        userName.set(pref.getString(Constants.PREF_USER_NAME, "로그인을 해주세요."))
        if (userName.get() == "로그인을 해주세요.") {
            loginBtnString.set("로그인")
        } else {
            loginBtnString.set("로그아웃")
        }
        autoUploadState.set(pref.getBoolean(Constants.PREF_AUTO_UPLOAD, false))
    }

    // 값 저장하기
    private fun savePreferences(boolean: Boolean) {
        val pref = getApplication<Application>().getSharedPreferences(Constants.PREF_NAME_LOGIN, MODE_PRIVATE)
        val editor = pref.edit()
        editor.putBoolean(Constants.PREF_AUTO_UPLOAD, boolean)
        editor.apply()
    }

    // 값(Key Data) 삭제하기
    private fun removePreferences() {
        val pref = getApplication<Application>().getSharedPreferences(Constants.PREF_NAME_LOGIN, MODE_PRIVATE)
        val editor = pref.edit()
        editor.remove(Constants.PREF_USER_NAME)
        editor.remove(Constants.PREF_USER_ID)
        editor.remove(Constants.PREF_USER_IMAGE)
        editor.apply()
    }

    // 값(ALL Data) 삭제하기
    private fun removeAllPreferences() {
        val pref = getApplication<Application>().getSharedPreferences(Constants.PREF_NAME_LOGIN, MODE_PRIVATE)
        val editor = pref.edit()
        editor.clear()
        editor.apply()
    }
}
