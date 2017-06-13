package com.unicolour.joyspace.controller.api

import com.unicolour.joyspace.dao.ProductDao
import com.unicolour.joyspace.model.ProductType
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseBody
import javax.servlet.http.HttpServletRequest


@Controller
class ProductController {
    @Autowired
    var productDao: ProductDao? = null

    @RequestMapping("/api/product/findByPrintStation", method = arrayOf(RequestMethod.GET))
    @ResponseBody
    fun findByPrintStation(
            request: HttpServletRequest,
            @RequestParam("printStationId") prnStationId: Int) : ResponseEntity<List<ProductDTO>> {

        val host: String? = request.getHeader("host");
        val protocal: String? = request.getHeader("x-forwarded-proto");

        var baseUrl: String = "http://localhost:6060"
        if (host != null && protocal != null) {
            baseUrl = protocal + "://" + host
        }

        val products = productDao!!.findAll();
        return ResponseEntity.ok(products.map {
            ProductDTO(
                    id = it.id,
                    name = it.name,
                    type = it.type,
                    sn = it.sn,
                    resolutionX = it.resolutionX,
                    resolutionY = it.resolutionY,
                    imageRequired = it.minImageCount,
                    remark = it.remark,
                    price = it.defaultPrice,
                    thumbnailUrl = "${baseUrl}/assets/product/thumb/${it.sn}.jpg"
            )
        }.toList())
    }
}

class ProductDTO(
        var id: Int = 0,
        var name: String = "",
        var type: Int = ProductType.PHOTO.value,
        var sn: String = "",
        var resolutionX: Int = 0,
        var resolutionY: Int = 0,
        var imageRequired: Int = 0,
        var remark: String? = null,
        var price: Int = 0,
        var thumbnailUrl: String =""
)



