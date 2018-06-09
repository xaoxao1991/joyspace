package com.unicolour.joyspace.service

import com.unicolour.joyspace.model.TPriceItem
import java.util.*

interface TPriceService {

    fun createtp(name: String,begin: Date, expire: Date, product_id: Int,tpriceItem: List<TPriceItem>): Boolean

    fun updatetp(name: String,begin: Date, expire: Date, product_id: Int)

    fun tpriceEnabled(id: Int): Boolean


}

