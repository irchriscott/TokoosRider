package com.portail.tokoosrider.models

class ServerResponse (
    val type: String,
    val message: String,
    val status: Int,
    val args: List<String>
){}