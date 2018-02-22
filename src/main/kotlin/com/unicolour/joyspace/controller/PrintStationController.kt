package com.unicolour.joyspace.controller

import com.unicolour.joyspace.dao.*
import com.unicolour.joyspace.dto.CommonRequestResult
import com.unicolour.joyspace.dto.ProductItem
import com.unicolour.joyspace.dto.ResultCode
import com.unicolour.joyspace.exception.ProcessException
import com.unicolour.joyspace.model.PrintStation
import com.unicolour.joyspace.model.PrintStationLoginSession
import com.unicolour.joyspace.model.ProductType
import com.unicolour.joyspace.service.ManagerService
import com.unicolour.joyspace.service.PrintStationService
import com.unicolour.joyspace.util.Pager
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.*
import org.springframework.web.servlet.ModelAndView
import java.util.*
import javax.servlet.http.HttpServletRequest

@Controller
class PrintStationController {

    @Autowired
    lateinit var printStationDao: PrintStationDao

    @Autowired
    lateinit var printStationService: PrintStationService

    @Autowired
    lateinit var positionDao: PositionDao

    @Autowired
    lateinit var adSetDao: AdSetDao

    @Autowired
    lateinit var productDao: ProductDao

    @Autowired
    lateinit var companyDao: CompanyDao

    @Autowired
    lateinit var printStationProductDao: PrintStationProductDao

    @Autowired
    lateinit var managerService: ManagerService

    @Autowired
    lateinit var printStationLoginSessionDao: PrintStationLoginSessionDao

    @RequestMapping("/printStation/list")
    fun printStationList(
            modelAndView: ModelAndView,
            @RequestParam(name = "pageno", required = false, defaultValue = "1") pageno: Int,
            @RequestParam(name = "inputPositionId", required = false, defaultValue = "0") inputPositionId: Int
    ): ModelAndView {

        val loginManager = managerService.loginManager

        if (loginManager == null) {
            modelAndView.viewName = "empty"
            return modelAndView
        }

        val pageable = PageRequest(pageno - 1, 20, Sort.Direction.ASC, "id")
        val printStations = if (inputPositionId > 0)
                printStationDao.findByCompanyIdAndPositionId(loginManager.companyId, inputPositionId, pageable)
            else
                printStationDao.findByCompanyId(loginManager.companyId, pageable)

        val pager = Pager(printStations.totalPages, 7, pageno - 1)
        modelAndView.model["pager"] = pager

        class PrintStationInfo(val printStation: PrintStation, val online: Boolean, val version: String)

        val time = Calendar.getInstance()
        time.add(Calendar.SECOND, 3600 - 30)

        modelAndView.model["positions"] = positionDao.findByCompanyId(loginManager.companyId)
        modelAndView.model["printStations"] = printStations.content.map {
            val session = printStationLoginSessionDao.findByPrintStationId(it.id)
            var online = false
            var version = ""

            if (session != null && session.expireTime.timeInMillis > time.timeInMillis) {    //自助机30秒之内访问过后台
                online = true
                version = if (session.version <= 0) "" else session.version.toString()
            }

            PrintStationInfo(it, online, version)
        }

        modelAndView.model["inputPositionId"] = inputPositionId
        modelAndView.model["viewCat"] = "business_mgr"
        modelAndView.model["viewContent"] = "printStation_list"
        modelAndView.viewName = "layout"

        return modelAndView
    }

    @RequestMapping(path = arrayOf("/printStation/edit"), method = arrayOf(RequestMethod.GET))
    fun editPrintStation(
            modelAndView: ModelAndView,
            @RequestParam(name = "id", required = true) id: Int
    ): ModelAndView {
        val loginManager = managerService.loginManager

        var supportedProductIdSet: Set<Int> = emptySet<Int>()

        var printStation: PrintStation? = null
        if (id > 0) {
            printStation = printStationDao.findOne(id)
            supportedProductIdSet = printStationProductDao.findByPrintStationId(id).map { it.productId }.toHashSet()
        }

        if (printStation == null) {
            printStation = PrintStation()
            printStation.printerType = "CY"
        }

        //val allProducts = productDao.findByCompanyIdOrderBySequenceAsc(loginManager!!.companyId)
        val allProducts = productDao.findAll()
                .sortedBy { it.sequence }
                .map {
                    ProductItem(
                            productId = it.id,
                            productType = it.template.type,
                            productName = it.name,
                            templateName = it.template.name,
                            selected = supportedProductIdSet.contains(it.id))
                }

        modelAndView.model["create"] = id <= 0
        modelAndView.model["printStation"] = printStation
        modelAndView.model["positions"] = positionDao.findByCompanyId(loginManager!!.companyId)
        modelAndView.model["companies"] = companyDao.findAll()
        modelAndView.model["adSets"] = adSetDao.findByCompanyIdOrPublicResourceIsTrue(loginManager.companyId)
        modelAndView.model["photo_products"] = allProducts.filter { it.productType == ProductType.PHOTO.value }
        modelAndView.model["template_products"] = allProducts.filter { it.productType == ProductType.TEMPLATE.value }
        modelAndView.model["id_photo_products"] = allProducts.filter { it.productType == ProductType.ID_PHOTO.value }
        modelAndView.model["productIds"] = allProducts.map { it.productId }.joinToString(separator = ",")
        modelAndView.viewName = "/printStation/edit :: content"

        return modelAndView
    }

    @RequestMapping(path = arrayOf("/printStation/edit"), method = arrayOf(RequestMethod.POST))
    @ResponseBody
    fun editPrintStation(
            request: HttpServletRequest,
            @RequestParam(name = "id", required = true) id: Int,
            @RequestParam(name = "companyId", required = false, defaultValue = "-1") companyId: Int,
            @RequestParam(name = "password", required = true) password: String,
            @RequestParam(name = "positionId", required = true) positionId: Int,
            @RequestParam(name = "proportion", required = false, defaultValue = "0") proportion: Double,
            @RequestParam(name = "printerType", required = true, defaultValue = "") printerType: String,
            @RequestParam(name = "adSetId", required = false, defaultValue = "-1") adSetId: Int,
            @RequestParam(name = "productIds", required = true) productIds: String
    ): Boolean {

        val selectedProductIds = productIds
                .split(',')
                .filter { !request.getParameter("product_${it}").isNullOrBlank() }
                .map { it.toInt() }
                .toSet()

        if (id <= 0) {
            printStationService.createPrintStation(companyId, password, positionId, (proportion * 10).toInt(), printerType, adSetId, selectedProductIds)
            return true
        } else {
            return printStationService.updatePrintStation(id, companyId, password, positionId, (proportion * 10).toInt(), printerType, adSetId, selectedProductIds)
        }
    }

    @RequestMapping(path = arrayOf("/printStation/activate"), method = arrayOf(RequestMethod.GET))
    fun activatePrintStation(modelAndView: ModelAndView): ModelAndView {
        val loginManager = managerService.loginManager

        val supportedProductIdSet: Set<Int> = emptySet<Int>()

        val printStation = PrintStation()

        //val allProducts = productDao.findByCompanyIdOrderBySequenceAsc(loginManager!!.companyId)
        val allProducts = productDao.findAll()
                .sortedBy { it.sequence }
                .map {
                    ProductItem(
                            productId = it.id,
                            productType = it.template.type,
                            productName = it.name,
                            templateName = it.template.name,
                            selected = supportedProductIdSet.contains(it.id))
                }

        modelAndView.model["printStation"] = printStation
        modelAndView.model["positions"] = positionDao.findByCompanyId(loginManager!!.companyId)
        modelAndView.model["photo_products"] = allProducts.filter { it.productType == ProductType.PHOTO.value }
        modelAndView.model["template_products"] = allProducts.filter { it.productType == ProductType.TEMPLATE.value }
        modelAndView.model["id_photo_products"] = allProducts.filter { it.productType == ProductType.ID_PHOTO.value }
        modelAndView.model["productIds"] = allProducts.map { it.productId }.joinToString(separator = ",")
        modelAndView.viewName = "/printStation/activate :: content"

        return modelAndView
    }

    @RequestMapping(path = arrayOf("/printStation/activate"), method = arrayOf(RequestMethod.POST))
    @ResponseBody
    fun activatePrintStation(
            request: HttpServletRequest,
            @RequestParam(name = "code", required = true) code: String,
            @RequestParam(name = "name", required = true) name: String,
            @RequestParam(name = "printStationPassword", required = true) password: String,
            @RequestParam(name = "positionId", required = true) positionId: Int,
            @RequestParam(name = "productIds", required = true) productIds: String
    ): CommonRequestResult {

        try {
            val selectedProductIds = productIds
                    .split(',')
                    .filter { !request.getParameter("product_${it}").isNullOrBlank() }
                    .map { it.toInt() }
                    .toSet()
            printStationService.activatePrintStation(code, name, password, positionId, selectedProductIds)
            return CommonRequestResult()
        } catch (e: ProcessException) {
            return CommonRequestResult(e.errcode, e.message)
        } catch (e: Exception) {
            e.printStackTrace()
            return CommonRequestResult(ResultCode.OTHER_ERROR.value, "创建自助机失败")
        }
    }

    @RequestMapping("/printStation/{id}")
    fun printStation(
            modelAndView: ModelAndView,
            @PathVariable("id") id: Int): ModelAndView {

        val printStation = printStationDao.findOne(id)

        if (printStation != null) {
            modelAndView.viewName = "/printStation/index"
        }
        else {
            modelAndView.viewName = "/printStation/notFound"
        }

        return modelAndView
    }
}
