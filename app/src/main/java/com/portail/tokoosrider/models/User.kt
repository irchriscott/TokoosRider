package com.portail.tokoosrider.models

class User (
    val id: Int,
    val name: String?,
    val email: String?,
    val phoneNumber: String,
    val country: String?,
    val city: String?,
    val token: String?
){}