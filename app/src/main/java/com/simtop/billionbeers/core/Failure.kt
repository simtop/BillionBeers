package com.simtop.billionbeers.core

//TODO try this
sealed class Failure{
    sealed class SignInError {
        sealed class SocialError(val message: String) : SignInError() {
            class GoogleError(message: String) : SocialError(message)
            class FacebookError(message: String) : SocialError(message)
        }
    }
}