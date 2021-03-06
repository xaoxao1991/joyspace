package com.unicolour.joyspace.service.impl

import com.unicolour.joyspace.dao.*
import com.unicolour.joyspace.model.*
import com.unicolour.joyspace.service.ManagerService
import com.unicolour.joyspace.service.PrintStationService
import com.unicolour.joyspace.service.ProductService
import com.unicolour.joyspace.service.TemplateService
import graphql.schema.DataFetcher
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.regex.Pattern
import javax.transaction.Transactional


@Service
open class ProductServiceImpl : ProductService {
    @Value("\${com.unicolour.joyspace.baseUrl}")
    lateinit var baseUrl: String

    @Value("\${com.unicolour.joyspace.assetsDir}")
    lateinit var assetsDir: String

    @Autowired
    lateinit var printStationProductDao: PrintStationProductDao

    @Autowired
    lateinit var productDao: ProductDao

    @Autowired
    lateinit var productImgFileDao: ProductImageFileDao

    @Autowired
    lateinit var managerService: ManagerService

    @Autowired
    lateinit var managerDao: ManagerDao

    @Autowired
    lateinit var templateDao: TemplateDao

    @Autowired
    lateinit var tplImgInfoDao: TemplateImageInfoDao

    @Autowired
    lateinit var printStationService: PrintStationService
    @Autowired
    lateinit var templateService: TemplateService

    override fun getProductsOfPrintStation(printStationId: Int): List<PrintStationProduct> {
        val products = printStationProductDao.findByPrintStationId(printStationId)
        return products.filter { !it.product.deleted }.sortedBy { it.product.sequence }
    }

    override fun getProductsOfPrintStationAndCommonProduct(printStationId: Int): List<Product> {
        val products = printStationProductDao.findByPrintStationId(printStationId).filter { !it.product.deleted }.sortedBy { it.product.sequence }.map { it.product }
        val temTemplateIds = templateService.queryTemplates(ProductType.TEMPLATE, "", true, "id asc").map { it.id }
        val temProducts = productDao.findByTemplateIdInAndDeletedOrderBySequence(temTemplateIds, false)
        val albumTemplateIds = templateService.queryTemplates(ProductType.ALBUM, "", true, "id asc").map { it.id }
        val albumProducts = productDao.findByTemplateIdInAndDeletedOrderBySequence(albumTemplateIds, false)
        return products.plus(temProducts).plus(albumProducts)
    }

    @Transactional
    override fun updateProduct(id: Int, name: String, remark: String, defPrice: Double, areaPrice: Double, piecePrice: Double, templateId: Int, refined: Int): Boolean {
        val product = productDao.findOne(id)
        if (product != null) {
            val tpl = templateDao.findOne(templateId)

            if (tpl != null) {
                product.name = name
                product.template = tpl
                product.defaultPrice = (defPrice * 100).toInt()
                product.areaPrice = (areaPrice * 100).toInt()
                product.piecePrice = (piecePrice * 100).toInt()
                product.deleted = false
                product.remark = remark
                product.refined = refined == 1
                productDao.save(product)
                return true
            }
        }

        return false
    }

    @Transactional
    override fun moveProduct(id: Int, up: Boolean): Boolean {
        val product = productDao.findOne(id)
        if (product != null) {
            var otherProduct: Product? = null
            if (up) {
                otherProduct = productDao.findFirstByCompanyIdAndSequenceLessThanOrderBySequenceDesc(product.companyId, product.sequence)
            }
            else {
                otherProduct = productDao.findFirstByCompanyIdAndSequenceGreaterThanOrderBySequence(product.companyId, product.sequence)
            }

            if (otherProduct == null) {
                return false
            }
            else {
                val t = product.sequence
                product.sequence = otherProduct.sequence
                otherProduct.sequence = t

                productDao.save(product)
                productDao.save(otherProduct)

                return true
            }
        }
        else {
            return false
        }
    }

    @Transactional
    override fun createProduct(name: String, remark: String, defPrice: Double, areaPrice: Double, piecePrice: Double, templateId: Int, refined: Int): Product? {
        val loginManager = managerService.loginManager
        val tpl = templateDao.findOne(templateId)

        if (tpl != null && loginManager != null) {
            val manager = managerDao.findOne(loginManager.managerId)

            val product = Product()

            product.name = name
            product.template = tpl
            product.defaultPrice = (defPrice * 100).toInt()
            product.deleted = false
            product.remark = remark
            product.companyId = 0
            product.areaPrice = (areaPrice * 100).toInt()
            product.piecePrice = (piecePrice * 100).toInt()
            product.sequence = productDao.getMaxProductSequence(0) + 1
            product.refined = refined == 1
            productDao.save(product)

            return product
        }

        return null
    }

    @Transactional
    override fun uploadProductImageFile(id: Int, type: ProductImageFileType, imageFile: MultipartFile?): ProductImageFile? {
        val product = productDao.findOne(id)
        if (product != null) {
            if (imageFile != null) {
                val uuid = UUID.randomUUID().toString()
                val file = File(assetsDir, "/product/images/$uuid")
                file.parentFile.mkdirs()

                imageFile.transferTo(file)

                val pb = ProcessBuilder("magick", "identify", file.absolutePath)

                val process = pb.start()

                var retStr:String = ""
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    retStr = reader.readText()
                }

                val retCode = process.waitFor()

                if (retCode != 0) {
                    file.delete()
                    throw IOException("not valid image file")
                }
                else {
                    val patternStr = Pattern.quote(file.absolutePath) + "\\s(\\w+)\\s.*"
                    val pattern = Pattern.compile(patternStr)
                    val matcher = pattern.matcher(retStr)

                    matcher.find()

                    var imgType = matcher.group(1).toLowerCase()
                    if (imgType == "jpeg") {
                        imgType = "jpg"
                    }

                    val productImgFile = ProductImageFile()
                    productImgFile.product = product
                    productImgFile.type = type.value
                    productImgFile.fileType = imgType
                    productImgFileDao.save(productImgFile)

                    val fileWithExt = File(assetsDir, "/product/images/${productImgFile.id}.$imgType")
                    file.renameTo(fileWithExt)

                    return productImgFile
                }
            }
        }

        return null
    }

    override fun deleteProductImageFile(imgFileId: Int): Boolean {
        val productImgFile = productImgFileDao.findOne(imgFileId)
        if (productImgFile != null) {
            productImgFileDao.delete(productImgFile)
            val fileWithExt = File(assetsDir, "/product/images/${productImgFile.id}.${productImgFile.fileType}")
            fileWithExt.delete()

            return true
        }
        else {
            return false
        }
    }

    private fun getTemplateImagesOfCurrentVersion(tpl: Template, context: HashMap<String, Any>): List<TemplateImageInfo> {
        val key = "_tplImgs_${tpl.id}.${tpl.currentVersion}"
        return context.computeIfAbsent(key, {
            println(key)
            tplImgInfoDao.findByTemplateIdAndTemplateVersion(tpl.id, tpl.currentVersion)
        }) as List<TemplateImageInfo>

    }

    override fun getDataFetcher(fieldName: String): DataFetcher<Any> {
        return DataFetcher<Any> { env ->
            val src = env.getSource<Any>()
            val product:Product
            var printStation:PrintStation? = null

            if (src is PrintStationProduct) {
                product = src.product
                printStation = src.printStation
            }
            else {
                product = src as Product
            }

            when (fieldName) {
                "remark" -> product.remark
                "id" -> product.id
                "name" -> product.name
                "version" -> {
                    val tpl = product.template
                    "${tpl.id}.${tpl.currentVersion}"
                }
                "type" -> {
                    val tpl = product.template
                    val type = ProductType.values().find{ it.value == tpl.type }
                    if (type == null) null else type.name
                }
                "typeInt" -> product.template.type
                "templateWidth" -> product.template.width
                "templateHeight" -> product.template.height
                "width" -> {
                    val tpl = product.template
                    if (tpl.type == ProductType.ID_PHOTO.value) {
                        val context = env.getContext<HashMap<String, Any>>()
                        val images = getTemplateImagesOfCurrentVersion(tpl, context)
                        images[0].width
                    }
                    else {
                        tpl.width
                    }
                }
                "height" -> {
                    val tpl = product.template
                    if (tpl.type == ProductType.ID_PHOTO.value) {
                        val context = env.getContext<HashMap<String, Any>>()
                        val images = getTemplateImagesOfCurrentVersion(tpl, context)
                        images[0].height
                    }
                    else {
                        tpl.height
                    }
                }
                "displaySize" -> {
                    val tpl = product.template
                    var w = tpl.width
                    var h = tpl.height

                    if (tpl.type == ProductType.ID_PHOTO.value) {
                        val context = env.getContext<HashMap<String, Any>>()
                        val images = getTemplateImagesOfCurrentVersion(tpl, context)

                        w = images[0].width
                        h = images[0].height
                    }

                    String.format("%1$.0f x %2$.0f mm", w, h)
                }
                "idPhotoMaskImageUrl" -> {
                    val tpl = product.template
                    if (tpl.type == ProductType.ID_PHOTO.value) {
                        "${baseUrl}/assets/template/preview/${tpl.id}_v${tpl.currentVersion}/mask.png"
                    }
                    else {
                        null
                    }
                }
                "imageRequired" -> product.template.minImageCount
                "thumbnailImageUrl" -> {
                    product.imageFiles
                            .filter { it.type == ProductImageFileType.THUMB.value }
                            .map { "$baseUrl/assets/product/images/${it.id}.${it.fileType}" }
                            .firstOrNull()
                }
                "previewImageUrls" -> {
                    product.imageFiles
                            .filter { it.type == ProductImageFileType.PREVIEW.value }
                            .map { "$baseUrl/assets/product/images/${it.id}.${it.fileType}" }
                }
                "templateImages" -> {
                    val context = env.getContext<HashMap<String, Any>>()
                    getTemplateImagesOfCurrentVersion(product.template, context)

                }
                "price" -> {
                    if (printStation == null) {
                        null
                    }
                    else {
                        val priceMap: Map<Int, Int> = printStationService.getPriceMap(printStation)
                        priceMap.getOrDefault(product.id, product.defaultPrice)
                    }
                }
                "templateUrl" -> {
                    val tpl = product.template

                    "$baseUrl/assets/template/preview/${tpl.id}_v${tpl.currentVersion}/template.svg"
                }
                else -> null
            }
        }
    }

    override fun queryProducts(pageNo: Int, pageSize: Int, companyId: Int, name: String, excludeDeleted: Boolean, order: String): Page<Product> {
        val orderField: String
        val asc: Boolean

        val t = order.indexOf(" ")
        if (t != -1) {
            orderField = order.substring(0, t)
            asc = order.substring(t + 1).equals("ASC", ignoreCase = true)
        } else {
            orderField = order
            asc = true
        }

        val pageReq = PageRequest(pageNo - 1, pageSize,
                Sort(Sort.Order(if (asc) Sort.Direction.ASC else Sort.Direction.DESC, orderField)))

        return productDao.queryProducts(pageReq, companyId, name, excludeDeleted)
    }

    override fun queryProducts(companyId: Int, name: String, excludeDeleted: Boolean, order: String): List<Product> {
        val orderField: String
        val asc: Boolean

        val t = order.indexOf(" ")
        if (t != -1) {
            orderField = order.substring(0, t)
            asc = order.substring(t + 1).equals("ASC", ignoreCase = true)
        } else {
            orderField = order
            asc = true
        }

        val sort = Sort(Sort.Order(if (asc) Sort.Direction.ASC else Sort.Direction.DESC, orderField))

        return productDao.queryProducts(companyId, name, excludeDeleted, sort)
    }

    @Transactional
    override fun deleteProductById(productId: Int): Boolean {
        val manager = managerService.loginManager
        val product = productDao.findOne(productId)

        if (manager != null && product != null && !product.deleted) {
            val isSuperAdmin = managerService.loginManagerHasRole("ROLE_SUPERADMIN")
            if (isSuperAdmin || product.companyId == manager.companyId) {
                product.deleted = true
                productDao.save(product)
                return true
            }
        }

        return false
    }
}

