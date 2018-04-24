package com.unicolour.joyspace.controller.api.v2

import com.google.gson.Gson
import com.unicolour.joyspace.dao.ProductDao
import com.unicolour.joyspace.dao.TemplateDao
import com.unicolour.joyspace.dto.*
import com.unicolour.joyspace.dto.common.RestResponse
import com.unicolour.joyspace.model.ProductImageFileType
import com.unicolour.joyspace.service.AdSetService
import com.unicolour.joyspace.service.ProductService
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RestController

@RestController
class ApiProductRoute {
    val logger = LoggerFactory.getLogger(this::class.java)
    @Autowired
    private lateinit var adSetService: AdSetService
    @Autowired
    private lateinit var productService: ProductService
    @Autowired
    private lateinit var productDao: ProductDao
    @Autowired
    private lateinit var templateDao: TemplateDao
    @Value("\${com.unicolour.joyspace.baseUrl}")
    private lateinit var baseUrl: String

    /**
     * 主页数据
     */
    @GetMapping(value = "/v2/app/homepage")
    fun showHomePage(): RestResponse {
        val advers = mutableListOf<Advert>()
        advers.add(Advert("ad_1", "轮播图", "www.baidu.com", "http://47.52.238.144:6600/doc/home_page/home_1.jpg"))
        advers.add(Advert("ad_2", "轮播图", "www.baidu.com", "http://47.52.238.144:6600/doc/home_page/home_2.jpg"))
        advers.add(Advert("ad_3", "轮播图", "www.baidu.com", "http://47.52.238.144:6600/doc/home_page/home_3.jpg"))
        advers.add(Advert("ad_4", "轮播图", "www.baidu.com", "http://47.52.238.144:6600/doc/home_page/home_4.jpg"))
        advers.add(Advert("ad_5", "轮播图", "www.baidu.com", "http://47.52.238.144:6600/doc/home_page/home_5.jpg"))
        val producTypes = mutableListOf<ProductType>()
        producTypes.add(ProductType(0, "普通照片", "首次冲印免费"))
        producTypes.add(ProductType(1, "证件照", "首次冲印免费"))
        producTypes.add(ProductType(2, "模版拼图", "免费模板随便用"))
        producTypes.add(ProductType(3, "相册", "打造属于你自己的回忆"))
        val homePage = HomePageVo(advers, producTypes)
        return RestResponse.ok(homePage)
    }


    /**
     * 获取某个类型的全部产品（规格／模版）信息
     */
    @GetMapping(value = "/v2/product/{type}")
    fun getProductsByType(@PathVariable("type") type: Int): RestResponse {
        val templateIds = templateDao.findByType(type).map { it.id }
        val products = productDao.findByTemplateIdInAndEnabledOrderBySequence(templateIds, true)
        val productVoList = products.map {
            val tpl = it.template
            val w = tpl.width
            val h = tpl.height
            val displaySize = String.format("%1$.0f x %2$.0f mm", w, h)
            val thumbnailImageUrl = it.imageFiles
                    .filter { it.type == ProductImageFileType.THUMB.value }
                    .map { "/assets/product/images/${it.id}.${it.fileType}" }
                    .firstOrNull()
            ProductVo(it.id, it.name, it.template.width, it.template.height, displaySize, tpl.minImageCount, it.remark, it.defaultPrice, thumbnailImageUrl)
        }
        return RestResponse.ok(productVoList)
    }

    /**
     * 获取某个产品（规格／模版）的详细信息
     */
    @GetMapping(value = "/v2/product/detail/{id}")
    fun getTemplateDetail(@PathVariable("id") id: Int): RestResponse {
        val IDPhotoData = """
            {
    "id": 9527,
    "version": 1.0,
    "name": "标准1寸照测试模板",
    "type": 2,
    "scenes": [
        {
            "id": 1,
            "name": "",
            "type": "page",
            "width": 2160,
            "height": 1440,
            "layers": [
                {
                    "id": 1,
                    "type": "background",
                    "images": [
                        {
                            "id": 1,
                            "type": "sticker",
                            "x": 0,
                            "y": 0,
                            "width": 2160,
                            "height": 1440,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": "http://47.52.238.144:6600/img/inch_1_test.png"
                        }
                    ]
                },
                {
                    "id": 2,
                    "type": "image",
                    "images": [
                        {
                            "id": 1,
                            "type": "user",
                            "x": 351,
                            "y": 215,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 2,
                            "type": "user",
                            "x": 719,
                            "y": 215,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 3,
                            "type": "user",
                            "x": 1087,
                            "y": 215,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 4,
                            "type": "user",
                            "x": 1455,
                            "y": 215,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 5,
                            "type": "user",
                            "x": 315,
                            "y": 729,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 6,
                            "type": "user",
                            "x": 719,
                            "y": 729,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 7,
                            "type": "user",
                            "x": 1087,
                            "y": 729,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        },
                        {
                            "id": 8,
                            "type": "user",
                            "x": 1455,
                            "y": 729,
                            "width": 354,
                            "height": 496,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        }
                    ]
                }
            ]
        }
    ]
}
            """
        val normalPhotoData = """
            {
    "id": 9528,
    "version": 1.0,
    "name": "普通模板照片测试模板",
    "type": 3,
    "scenes": [
        {
            "id": 1,
            "name": "",
            "type": "page",
            "width": 2160,
            "height": 1440,
            "layers": [
                {
                    "id": 1,
                    "type": "background",
                    "images": [
                        {
                            "id": 1,
                            "type": "sticker",
                            "x": 0,
                            "y": 0,
                            "width": 2160,
                            "height": 1440,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": "http://47.52.238.144:6600/img/background_layer.png"
                        }
                    ]
                },
                {
                    "id": 2,
                    "type": "image",
                    "images": [
                        {
                            "id": 1,
                            "type": "user",
                            "x": 171,
                            "y": 171,
                            "width": 1818,
                            "height": 1098,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": ""
                        }
                    ]
                },
                {
                    "id": 2,
                    "type": "image",
                    "images": [
                        {
                            "id": 1,
                            "type": "user",
                            "x": 0,
                            "y": 0,
                            "width": 2160,
                            "height": 1440,
                            "angleClip": 0,
                            "bgcolor": "",
                            "resourceURL": "http://47.52.238.144:6600/img/front_layer.png"
                        }
                    ]
                }
            ]
        }
    ]
}
            """
        val template = Gson().fromJson(if (id == 1) normalPhotoData else IDPhotoData, TemplateVo::class.java)
        return RestResponse.ok(template)
    }

}