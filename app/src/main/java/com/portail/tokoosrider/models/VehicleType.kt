package com.portail.tokoosrider.models

import com.portail.tokoosrider.models.VehicleFare

class VehicleType(
    val id: Int,
    val name: String,
    val size: Int,
    val fare: VehicleFare
) {}