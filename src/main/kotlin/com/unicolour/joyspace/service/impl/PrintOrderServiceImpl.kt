package com.unicolour.joyspace.service.impl

import com.fasterxml.jackson.databind.ObjectMapper
import com.unicolour.joyspace.dao.*
import com.unicolour.joyspace.dto.*
import com.unicolour.joyspace.exception.ProcessException
import com.unicolour.joyspace.model.*
import com.unicolour.joyspace.service.*
import graphql.schema.DataFetcher
import graphql.schema.DataFetchingEnvironment
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.core.io.Resource
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpEntity
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpMethod
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.client.RestTemplate
import org.springframework.web.multipart.MultipartFile
import java.io.IOException
import java.io.InputStreamReader
import java.io.StringReader
import java.math.BigInteger
import java.net.URL
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.SecureRandom
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.annotation.PostConstruct
import javax.transaction.Transactional
import javax.xml.bind.JAXBContext
import javax.xml.bind.Unmarshaller
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.experimental.and


@Service
open class PrintOrderServiceImpl : PrintOrderService {
    companion object {
        val logger = LoggerFactory.getLogger(PrintOrderServiceImpl::class.java)
    }

    val wxEntTransferExecutor: ExecutorService = Executors.newFixedThreadPool(1)

    @Value("\${com.unicolour.joyspace.baseUrl}")
    lateinit var baseUrl: String

    @Value("\${com.unicolour.joyspace.wxPayTransferCharge}")
    var wxPayTransferCharge: Double = 0.0

    @Autowired
    lateinit var printStationDao: PrintStationDao

    @Autowired
    lateinit var companyService: CompanyService

    @Autowired
    lateinit var managerService: ManagerService

    @Autowired
    lateinit var printStationService: PrintStationService

    @Autowired
    lateinit var couponService: CouponService

    @Autowired
    lateinit var imageService: ImageService

    @Autowired
    lateinit var userLoginSessionDao: UserLoginSessionDao

    @Autowired
    lateinit var printOrderDao: PrintOrderDao

    @Autowired
    lateinit var companyDao: CompanyDao

    @Autowired
    lateinit var userDao: UserDao

    @Autowired
    lateinit var printOrderItemDao: PrintOrderItemDao

    @Autowired
    lateinit var userImageFileDao: UserImageFileDao

    @Autowired
    lateinit var printOrderImageDao: PrintOrderImageDao

    @Autowired
    lateinit var wxEntTransferRecordDao: WxEntTransferRecordDao

    @Autowired
    lateinit var wxEntTransferRecordItemDao: WxEntTransferRecordItemDao

    @Autowired
    lateinit var productDao: ProductDao

    @Autowired
    lateinit var templateImageInfoDao: TemplateImageInfoDao

    @Autowired
    lateinit var couponDao: CouponDao

    @Autowired
    lateinit var userCouponDao: UserCouponDao

    @Autowired
    lateinit var secureRandom: SecureRandom

    @Autowired
    lateinit var restTemplate: RestTemplate

    @Autowired
    lateinit var objectMapper: ObjectMapper

    @Autowired
    lateinit var transactionTemplate: TransactionTemplate

    //小程序appid
    @Value("\${com.unicolour.wxAppId}")
    lateinit var wxAppId: String

    @Value("\${com.unicolour.wxMchId}")  //商户id
    lateinit var wxMchId: String

    @Value("\${com.unicolour.wxPayKey}")
    lateinit var wxPayKey: String

    //微信支付关联的公众号appid
    @Value("\${com.unicolour.wxmpAppId}")
    lateinit var wxmpAppId: String

    private lateinit var wxUnifyOrderResultUnmarshaller: Unmarshaller
    private lateinit var wxPayNotifyUnmarshaller: Unmarshaller
    private lateinit var wxEnterprisePayResultUnmarshaller: Unmarshaller

    @PostConstruct
    fun initialize() {
        wxUnifyOrderResultUnmarshaller = JAXBContext.newInstance(WxUnifyOrderResult::class.java).createUnmarshaller()
        wxPayNotifyUnmarshaller = JAXBContext.newInstance(WxPayNotify::class.java).createUnmarshaller()
        wxEnterprisePayResultUnmarshaller = JAXBContext.newInstance(WxEnterprisePayResult::class.java).createUnmarshaller()
    }

    @Transactional
    override fun createOrder(orderInput: OrderInput): PrintOrder {
        val session = userLoginSessionDao.findOne(orderInput.sessionId)

        if (session == null) {
            throw ProcessException(1, "用户未登录")
        }

        val printStation = printStationDao.findOne(orderInput.printStationId)

        if (printStation == null) {
            throw ProcessException(2, "没有找到指定的自助机")
        }

        val ret = calculateOrderFee(orderInput)

        val newOrder = PrintOrder()
        newOrder.orderNo = createOrderNo()
        newOrder.companyId = printStation.companyId
        newOrder.createTime = Calendar.getInstance()
        newOrder.updateTime = newOrder.createTime
        newOrder.printStationId = orderInput.printStationId
        newOrder.userId = session.userId
        newOrder.totalFee = ret.first
        newOrder.discount = ret.second
        //XXX newOrder.coupon = orderInput.coupon
        newOrder.payed = false
        newOrder.imageFileUploaded = false
        newOrder.downloadedToPrintStation = false
        newOrder.printedOnPrintStation = false
        newOrder.transfered = false
        newOrder.transferProportion = printStation.transferProportion
        newOrder.pageCount = orderInput.orderItems.sumBy { it.copies }

        val orderItems = ArrayList<PrintOrderItem>()
        newOrder.printOrderItems = orderItems

        printOrderDao.save(newOrder)

        for (orderItemInput in orderInput.orderItems) {
            //XXX 检查自助机是否支持此产品
            val product = productDao.findOne(orderItemInput.productId)

            val newOrderItem = PrintOrderItem()
            newOrderItem.copies = orderItemInput.copies
            newOrderItem.printOrder = newOrder
            newOrderItem.productId = orderItemInput.productId
            newOrderItem.productType = product.template.type
            newOrderItem.productVersion = orderItemInput.productVersion

            val orderImages = ArrayList<PrintOrderImage>()
            newOrderItem.orderImages = orderImages

            printOrderItemDao.save(newOrderItem)

            orderItems.add(newOrderItem)

            val tplVerSplit = orderItemInput.productVersion.split('.')
            val tplId = tplVerSplit[0].toInt()
            val tplVer = tplVerSplit[1].toInt()

            val tplImages = templateImageInfoDao.findByTemplateIdAndTemplateVersion(tplId, tplVer)
            for (tplImg in tplImages.filter { it.userImage }.distinctBy { it.name }) {
                var userImgId = 0
                var processParams: String? = null
                val orderItemImages = orderItemInput.images
                if (orderItemImages != null) {
                    val orderItemImg = orderItemImages.find { it.name == tplImg.name }
                    if (orderItemImg != null) {
                        userImgId = orderItemImg.imageId

                        val userImgFile = userImageFileDao.findOne(userImgId)
                        if (userImgFile != null) {
                            if (userImgFile.userId != session.userId) {
                                throw ProcessException(3, "图片不属于指定用户")
                            }
                        }

                        processParams = objectMapper.writeValueAsString(ImageProcessParams(orderItemImg))
                    }
                }

                val orderImg = PrintOrderImage()
                orderImg.orderId = newOrder.id
                orderImg.orderItemId = newOrderItem.id
                orderImg.name = tplImg.name
                orderImg.userImageFile = if (userImgId == 0) null else userImageFileDao.findOne(userImgId)
                orderImg.processParams = processParams
                orderImg.status = PrintOrderImageStatus.CREATED.value

                printOrderImageDao.save(orderImg)

                orderImages.add(orderImg)
            }
        }

        return newOrder
    }

    private fun createOrderNo(): String {
        val dateTime = SimpleDateFormat("yyyyMMdd-HHmmss").format(Date())
        var i = 0
        var orderNo:String
        do {
            orderNo = if (i == 0) dateTime else "$dateTime-$i"
            i++
        } while (printOrderDao.existsByOrderNo(orderNo))

        return orderNo
    }

    override val imageFilesDataFetcher: DataFetcher<Array<UserImageFile>>
        get() {
            return DataFetcher { env ->
                val printOrderItem = env.getSource<PrintOrderItem>()
                //printOrderItem.userImageFiles.toTypedArray()
                //XXX
                emptyArray<UserImageFile>()
            }
        }

    @Transactional
    override fun uploadOrderItemImage(sessionId: String, orderItemId: Int, name:String, imageProcessParam: ImageProcessParams?, imgFile: MultipartFile?): Boolean {
        val imgInfo = imageService.uploadImage(sessionId, imgFile)
        if (imgInfo.errcode == 0) {
            val orderImg = printOrderImageDao.findByOrderItemIdAndName(orderItemId, name)
            if (orderImg == null) {
                throw ProcessException(2, "没有此名称的图片")
            }
            else {
                orderImg.userImageFile = userImageFileDao.findOne(imgInfo.imageId)
                orderImg.processParams = if (imageProcessParam == null) "" else objectMapper.writeValueAsString(imageProcessParam)
                orderImg.status = PrintOrderImageStatus.UPLOADED.value

                printOrderImageDao.save(orderImg)
                return checkOrderImageUploaded(orderImg.orderId)
            }
        }
        else {
            throw ProcessException(1, "上传图片失败")
            //XXX
        }
    }

    //检查是否所有订单图片都已上传，如果都上传了返回true并修改订单状态
    @Synchronized
    private fun checkOrderImageUploaded(orderId: Int) : Boolean {
        val missingImageCount = printOrderImageDao.countByOrderIdAndUserImageFileIdIsNull(orderId)  //userImageFileId is null 表示此订单图片还没有上传
        if (missingImageCount == 0L) {
            val order = printOrderDao.findOne(orderId)
            order.imageFileUploaded = true
            order.updateTime = Calendar.getInstance()
            printOrderDao.save(order)

            printStationService.createPrintStationTask(order.printStationId, PrintStationTaskType.PROCESS_PRINT_ORDER, order.id.toString())

            return true
        }
        else {
            return false
        }
    }

    private fun orderToDTO(order: PrintOrder): PrintOrderDTO {
        val orderItemDTOs = ArrayList<PrintOrderItemDTO>()

        order.printOrderItems.forEach {
            val imageDTOs = ArrayList<PrintOrderImageDTO>()
            it.orderImages.forEach { img ->
                val userImgFile = img.userImageFile!!

                imageDTOs += PrintOrderImageDTO(
                        id = img.id,
                        name = img.name,
                        processParams = img.processParams,
                        userImageFile = UserImageFileDTO(
                                type = userImgFile.type,
                                width = userImgFile.width,
                                height = userImgFile.height,
                                url = "${baseUrl}/assets/user/${userImgFile.userId}/${userImgFile.sessionId}/${userImgFile.fileName}.${userImgFile.type}",
                                fileName = userImgFile.fileName
                        )
                )
            }

            orderItemDTOs += PrintOrderItemDTO(
                    id = it.id,
                    copies = it.copies,
                    productId = it.productId,
                    productType = it.productType,
                    productVersion = it.productVersion,
                    orderImages = imageDTOs)
        }

        val user = userDao.findOne(order.userId)

        return PrintOrderDTO(order.id, user?.nickName, orderItemDTOs)
    }

    override fun getPrintOrderDTO(printOrderId: Int): PrintOrderDTO? {
        val printOrder = printOrderDao.findOne(printOrderId)
        return if (printOrder == null) null else orderToDTO(printOrder)
    }

    override fun addReprintOrderTask(printOrderId: Int, printStationId: Int) {
        val loginManager = managerService.loginManager
        val order = printOrderDao.findOne(printOrderId)
        val printStation = printStationDao.findOne(printStationId)

        if (loginManager == null) {
            throw ProcessException(ResultCode.MANAGER_NOT_LOG_IN)
        }

        if (order == null) {
            throw ProcessException(ResultCode.PRINT_ORDER_NOT_FOUND)
        }
        else if (order.companyId != loginManager.companyId) {
            throw ProcessException(ResultCode.PRINT_ORDER_NOT_BELONG_TO_COMPANY)
        }

        if (printStation == null) {
            throw ProcessException(ResultCode.PRINT_STATION_NOT_FOUND)
        }
        else if (printStation.companyId != loginManager.companyId) {
            throw ProcessException(ResultCode.PRINT_STATION_NOT_BELONG_TO_COMPANY)
        }

        if (!printStationService.orderReprintTaskExists(printStationId, order.id)) {
            printStationService.createPrintStationTask(printStationId, PrintStationTaskType.PROCESS_PRINT_ORDER, order.id.toString())
        }
        else {
            throw ProcessException(ResultCode.REPRINT_TASK_EXISTS)
        }
    }

    override val printStationPrintOrdersDataFetcher: DataFetcher<List<PrintOrder>>
        get() {
            return DataFetcher { env ->
                val printStationSessionId = env.getArgument<String>("sessionId")

                val session = printStationService.getPrintStationLoginSession(printStationSessionId)
                if (session == null) {
                    throw org.springframework.security.access.AccessDeniedException("PrintStation login session invalid")
                }
                else {
                    val time =  Calendar.getInstance()
                    time.add(Calendar.DAY_OF_MONTH, -1)   //1天之内的订单

                    printOrderDao.findUnDownloadedPrintOrders(session.printStationId, time)
                }
            }
        }

    override val printOrderDataFetcher: DataFetcher<PrintOrderResult>
        get() {
            return DataFetcher { env ->
                val userSessionId = env.getArgument<String>("sessionId")
                val printOrderId = env.getArgument<Int>("printOrderId")

                val session = userLoginSessionDao.findOne(userSessionId)
                if (session == null) {
                    PrintOrderResult(1, "用户没有登录")
                }
                else {
                    val printOrder = printOrderDao.findOne(printOrderId)
                    if (printOrder != null && printOrder.userId == session.userId) {
                        PrintOrderResult(0, null, printOrder)
                    }
                    else {
                        PrintOrderResult(2, "不是此用户的订单")
                    }
                }
            }
        }

    override val printerOrderDownloadedDataFetcher: DataFetcher<GraphQLRequestResult>
        get() {
            return DataFetcher { env ->
                transactionTemplate.execute {
                    updatePrintOrderState(env, "downloaded")
                }
            }
        }

    override val printerOrderPrintedDataFetcher: DataFetcher<GraphQLRequestResult>
        get() {
            return DataFetcher { env ->
                transactionTemplate.execute {
                    updatePrintOrderState(env, "printed")
                }
            }
        }

    override val updatePrintOrderImageStatusDataFetcher: DataFetcher<GraphQLRequestResult>
        get() {
            return DataFetcher { env ->
                updatePrintOrderImageStatus(env)
            }
        }

    override val wxUserNickNameDataFetcher: DataFetcher<String?>
        get() {
            return DataFetcher { env ->
                val printOrder = env.getSource<PrintOrder>()
                val user = userDao.findOne(printOrder.userId)
                user?.nickName
            }
        }

    private fun updatePrintOrderImageStatus(env: DataFetchingEnvironment): GraphQLRequestResult {
        val sessionId = env.getArgument<String>("sessionId")
        val printOrderImageId: Int = env.getArgument<Int>("printOrderImageId")
        val status: Int = env.getArgument<Int>("status")


        val loginSession = printStationService.getPrintStationLoginSession(sessionId)
        return if (loginSession == null) {
            GraphQLRequestResult(ResultCode.INVALID_PRINT_STATION_LOGIN_SESSION)
        } else {
            val printOrderImage = printOrderImageDao.findOne(printOrderImageId)

            if (printOrderImage == null) {
                GraphQLRequestResult(ResultCode.PRINT_ORDER_NOT_FOUND)
            }
            else {
                printOrderImage.status = status
                printOrderImageDao.save(printOrderImage)
                GraphQLRequestResult(ResultCode.SUCCESS)
            }
        }
    }

    private fun updatePrintOrderState(env: DataFetchingEnvironment, state: String): GraphQLRequestResult {
        val sessionId = env.getArgument<String>("sessionId")
        val printOrderId = env.getArgument<Int>("printOrderId")

        val loginSession = printStationService.getPrintStationLoginSession(sessionId)
        return if (loginSession == null) {
            GraphQLRequestResult(ResultCode.INVALID_PRINT_STATION_LOGIN_SESSION)
        } else {
            val order = printOrderDao.findOne(printOrderId)
            if (order != null) {
//                if (order.printStationId != loginSession.printStationId) {
//                    GraphQLRequestResult(ResultCode.NOT_IN_THIS_PRINT_STATION)
//                } else {
                    if (state == "downloaded") {
                        order.downloadedToPrintStation = true
                        order.updateTime = Calendar.getInstance()
                    }
                    else if (state == "printed") {
                        order.printedOnPrintStation = true
                        order.updateTime = Calendar.getInstance()

                        printStationService.printStationTaskFetched(order.printStationId, order.id)
                    }

                    printOrderDao.save(order)
                    GraphQLRequestResult(ResultCode.SUCCESS)
//                }
            } else {
                GraphQLRequestResult(ResultCode.PRINT_ORDER_NOT_FOUND)
            }
        }
    }

//    private fun calcTransferAmount(order: PrintOrder): Int {
//        var amount:Double = (order.totalFee - order.discount).toDouble()
//        amount *= (1 - wxPayTransferCharge)   //扣除微信支付手续费
//
//        val proportion = order.transferProportion / 1000.0
//        val ret = if (proportion > 0.5) {
//            (amount * proportion + 0.5).toInt()
//        }
//        else {
//            (amount - amount * (1.0 - proportion) + 0.5).toInt()
//        }
//        return ret
//    }

    //订单金额和手续费计算结果 (各变量单位都是分)
    private class OrdersAmountAndTransferFeeCalcResult(
            val totalAmount: Int,                        //总金额(扣除手续费之前的)
            val totalTransferFee: Int,                   //总手续费
            val orderIdToTransferFeeMap: Map<Int, Int>)   //订单id -> 订单手续费
    {
        //总的转账金额
        val totalTransferAmount: Int
            get() = totalAmount - totalTransferFee
    }

    //计算多个订单的转账金额，手续费以及其中每个订单的手续费
    private fun calcOrdersAmountAndTransferFee(orders: List<PrintOrder>) : OrdersAmountAndTransferFeeCalcResult{
        val orderIdToTransferFeeMap = HashMap<Int, Int>()
        var totalAmount = 0   //总金额

        var orderTransferFeeAddUp = 0   //每个订单单独计算的手续费的累加结果
        for (order in orders) {
            val orderAmount = order.totalFee - order.discount
            totalAmount += orderAmount

            val orderTransferFee = (orderAmount.toDouble() * wxPayTransferCharge + 0.5).toInt()
            orderIdToTransferFeeMap[order.id] = orderTransferFee

            orderTransferFeeAddUp += orderTransferFee
        }

        val totalTransferFee:Int = Math.ceil(totalAmount.toDouble() * wxPayTransferCharge).toInt()  //根据订单总金额计算的手续费
        if (totalTransferFee != orderTransferFeeAddUp) {
            val maxAmountOrder = orders.maxBy { it.totalFee - it.discount }
            if (maxAmountOrder != null && orderIdToTransferFeeMap.containsKey(maxAmountOrder.id)) {
                orderIdToTransferFeeMap[maxAmountOrder.id] =
                        totalTransferFee - (orderTransferFeeAddUp - orderIdToTransferFeeMap[maxAmountOrder.id]!!)
            }
        }

        return OrdersAmountAndTransferFeeCalcResult(totalAmount, totalTransferFee, orderIdToTransferFeeMap)
    }

    private fun createTradeNo(): String {
        val dateTime = SimpleDateFormat("yyyyMMdd").format(Date())
        var tradeNo:String
        do {
            val randomStr = BigInteger(4 * 8, secureRandom).toString(36).toUpperCase()
            tradeNo = "$dateTime$randomStr"
        } while (wxEntTransferRecordDao.existsByTradeNo(tradeNo))

        return tradeNo
    }

    private fun startWxEntTransfer(orders: List<PrintOrder>, orderAmountAndFee: OrdersAmountAndTransferFeeCalcResult) {
        val account = companyService.getAvailableWxAccount(orders[0].companyId)
        if (account == null) {
            return
        }

        synchronized(this, {
            val record = WxEntTransferRecord()
            record.amount = orderAmountAndFee.totalTransferAmount
            record.companyId = account.companyId
            record.transferTime = Calendar.getInstance()
            record.tradeNo = createTradeNo()
            record.receiverName = account.name
            record.receiverOpenId = account.openId

            wxEntTransferRecordDao.save(record)

            orders.forEach { order ->
                val transferRecordItem = wxEntTransferRecordItemDao.findByPrintOrderId(order.id)
                if (transferRecordItem == null) {
                    val recordItem = WxEntTransferRecordItem()
                    recordItem.charge = orderAmountAndFee.orderIdToTransferFeeMap[order.id]!!
                    recordItem.amount = order.totalFee - order.discount - recordItem.charge
                    recordItem.printOrderId = order.id
                    recordItem.recordId = record.id

                    wxEntTransferRecordItemDao.save(recordItem)

                    order.transfered = true
                    printOrderDao.save(order)
                } else {
                    logger.info("No available WxAccount for companyId=${order.companyId}")
                }
            }

            doWxEntTransfer(record, orders)
        })
    }

    //每天结束前的批量转账
    @Scheduled(cron = "0 55 23 * * *")
    open fun doBatchTransfer() {
        val companies = companyDao.findAll()
        companies.forEach{ company ->
            wxEntTransferExecutor.submit {
                try {
                    val notTransferedOrders = printOrderDao.findByCompanyIdAndPayedIsTrueAndTransferedIsFalse(company.id)
                    val ordersAmountAndFee = calcOrdersAmountAndTransferFee(notTransferedOrders)

                    if (ordersAmountAndFee.totalTransferAmount > 100) {
                        startWxEntTransfer(notTransferedOrders, ordersAmountAndFee)
                        Thread.sleep(5000)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    override fun calculateOrderFee(orderInput: OrderInput) : Pair<Int, Int> {
        val session = userLoginSessionDao.findOne(orderInput.sessionId)
        //XXX
        //if (session == null) {
        //}

        val printStation = printStationDao.findOne(orderInput.printStationId)
        val priceMap = printStationService.getPriceMap(printStation)

        val productIdObjMap: MutableMap<Int, Product> = HashMap()
        var totalFee = 0
        var discount = 0

        for (printOrderItem in orderInput.orderItems) {
            val orderItemFee:Int = priceMap.getOrElse(printOrderItem.productId, {
                val product = productIdObjMap.computeIfAbsent(printOrderItem.productId, { productId -> productDao.findOne(productId) })
                product.defaultPrice
            })
            totalFee += orderItemFee * printOrderItem.copies
        }

        //XXX
        if (orderInput.couponId > 0) {
            val curTime = System.currentTimeMillis()

            val userCoupon = userCouponDao.findByUserIdAndCouponId(session.userId, orderInput.couponId)
            if (userCoupon == null) {
                throw ProcessException(1, "没有领取此优惠券")
            }
            else {   //XXX 检查使用次数,产品等
                val coupon = couponDao.findOne(orderInput.couponId)
                if (coupon == null) {
                    throw ProcessException(1, "指定的优惠券不可用")
                } else if (totalFee < coupon.minExpense) {
                    throw ProcessException(1, "没有达到最低消费金额")
                } else {
                    val couponCheckResult = couponService.checkCouponUse(orderInput.couponId, session.userId, orderInput.printStationId)
                    if (couponCheckResult == CouponValidateResult.VALID) {
                        discount = coupon.discount
                    }
                    else {
                        throw ProcessException(1, couponCheckResult.desc)
                    }
                }
            }
        }

        return Pair(totalFee, discount)
    }

    override fun startPayment(orderId: Int): WxPayParams {
        val order = printOrderDao.findOne(orderId)
        val company = companyDao.findOne(order.companyId)
        val wxPayConfig = company.weiXinPayConfig

        var appId: String = wxAppId
        var mchId: String = wxMchId
        var payKey: String = wxPayKey

        if (wxPayConfig != null && wxPayConfig.appId != null && wxPayConfig.mchId != null && wxPayConfig.keyVal != null) {
            appId = wxPayConfig.appId!!
            mchId = wxPayConfig.mchId!!
            payKey = wxPayConfig.keyVal!!
        }

        val user = userDao.findOne(order.userId)
        val openId: String = user?.wxOpenId ?: ""

        var nonceStr: String = BigInteger(32 * 8, secureRandom).toString(36).toUpperCase()
        if (nonceStr.length > 32) {
            nonceStr = nonceStr.substring(0, 32)
        }
        val notifyUrl = "$baseUrl/wxpay/notify"
        val ipAddress: String = java.net.InetAddress.getByName(URL(baseUrl).host).hostAddress

        val requestBody = getPaymentRequestParams(payKey, TreeMap(hashMapOf<String, String>(
                "appid" to appId,
                "body" to "优利绚彩-照片打印",
                "mch_id" to mchId,
                "nonce_str" to nonceStr,
                "notify_url" to notifyUrl,
                "openid" to openId,
                "out_trade_no" to order.orderNo,
                "spbill_create_ip" to ipAddress,
                "total_fee" to (order.totalFee - order.discount).toString(),
                "trade_type" to "JSAPI"
        )))

        val headers = HttpHeaders()
        headers.set(HttpHeaders.CONTENT_TYPE, "text/xml;charset=UTF-8")

        val request = HttpEntity<String>(requestBody, headers)
        val response = restTemplate.exchange(
                "https://api.mch.weixin.qq.com/pay/unifiedorder",
                HttpMethod.POST,
                request,
                Resource::class.java)


        InputStreamReader(response.body.inputStream, StandardCharsets.UTF_8).use {
            val resultStr = String(response.body.inputStream.readBytes(), StandardCharsets.UTF_8)
            println(resultStr)

            val result = wxUnifyOrderResultUnmarshaller.unmarshal(StringReader(resultStr)) as WxUnifyOrderResult

            if (result.return_code == "SUCCESS" && result.result_code == "SUCCESS") {
                return createWxPayParams(payKey, result, nonceStr)
            }
            else {
                throw ProcessException(3, "return_code=${result.return_code}, result_code=${result.result_code}")
            }
        }
    }

    private fun createWxPayParams(payKey:String, res: WxUnifyOrderResult, nonceStr: String): WxPayParams {
        val timeStamp = (System.currentTimeMillis() / 1000).toString()

        val kvs = "appId=$wxAppId" +
                "&nonceStr=$nonceStr" +
                "&package=prepay_id=${res.prepay_id}" +
                "&signType=MD5" +
                "&timeStamp=$timeStamp" +
                "&key=$payKey"

        val md5Digist = MessageDigest.getInstance("MD5")
        val sign = bytesToHexString(md5Digist.digest(kvs.toByteArray(StandardCharsets.US_ASCII)))

        return WxPayParams(
                timeStamp = timeStamp,
                nonceStr = nonceStr,
                pkg = "prepay_id=${res.prepay_id}",
                paySign = sign
        )
    }

    @Transactional
    override fun processWxPayNotify(requestBodyStr: String): String? {
        //XXX 记录到文件中
        //XXX 签名检查
        val result = wxPayNotifyUnmarshaller.unmarshal(StringReader(requestBodyStr)) as WxPayNotify

        if (result.appid != wxAppId) {
            return "错误的AppId"
        }
        else if (result.mch_id != wxMchId) {
            return "错误的商户号"
        }
        else if (result.out_trade_no == null) {
            return "订单号为空"
        }
        else {
            val printOrder = printOrderDao.findByOrderNo(result.out_trade_no!!)
            if (printOrder == null) {
                return "没有找到订单"
            }
            else {
                //XXX 检查金额

                printOrder.payed = true
                printOrder.updateTime = Calendar.getInstance()
                printOrderDao.save(printOrder)

                //转账
                wxEntTransferExecutor.submit {
                    transactionTemplate.execute {
                        try {
                            checkStartWxEntTransfer(printOrder)
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                }
                return null
            }
        }
    }

    /** 检查是否可以开始微信转账给投放商, 如果金额不够，检查是否可以和之前未转账的订单一起批量转，如果可以的话开始转账 */
    private fun checkStartWxEntTransfer(printOrder: PrintOrder) {
        val orderAmountAndFee = calcOrdersAmountAndTransferFee(Collections.singletonList(printOrder))
        if (!printOrder.transfered && orderAmountAndFee.totalTransferAmount > 100) {
            startWxEntTransfer(Collections.singletonList(printOrder), orderAmountAndFee)
        } else {
            val notTransferedOrders = printOrderDao.findByCompanyIdAndPayedIsTrueAndTransferedIsFalse(printOrder.companyId)
            val batchOrdersAmountAndFee = calcOrdersAmountAndTransferFee(notTransferedOrders)

            if (batchOrdersAmountAndFee.totalTransferAmount > 100) {
                startWxEntTransfer(notTransferedOrders, batchOrdersAmountAndFee)
            }
        }
    }

    private fun getPaymentRequestParams(payKey: String, varMap: TreeMap<String, String>): String {
        val sb = StringBuilder("<xml>")
        val sj = StringJoiner("&")

        for ((key, value) in varMap) {
            if (!value.isBlank()) {
                sj.add("$key=$value")
            }

            sb.append("<$key>$value</$key>")
        }

        sj.add("key=$payKey")

        val md5Digist = MessageDigest.getInstance("MD5")
        val sign = bytesToHexString(md5Digist.digest(sj.toString().toByteArray(StandardCharsets.UTF_8)))

        sb.append("<sign>$sign</sign>")
        sb.append("</xml>")

        return sb.toString()
    }

    private fun bytesToHexString(bytes: ByteArray): String {
        val buf = StringBuilder()
        for (b in bytes) {
            buf.append(String.format("%02X", b and 0xff.toByte()))
        }
        return buf.toString()
    }

    private fun doWxEntTransfer(record: WxEntTransferRecord, orders: List<PrintOrder>) {
        logger.info("Start WxEntTransfer for companyId=${record.companyId}, receiverOpenId=${record.receiverOpenId}, amount=${record.amount}")

        var nonceStr: String = BigInteger(32 * 8, secureRandom).toString(36).toUpperCase()
        if (nonceStr.length > 32) {
            nonceStr = nonceStr.substring(0, 32)
        }

        val ipAddress: String = java.net.InetAddress.getByName(URL(baseUrl).host).hostAddress

        var desc = "悦印订单款_" + orders.joinToString("_") { it.id.toString() }
        if (desc.length > 85) {
            desc = desc.substring(0, 82) + "..."
        }

        val params = TreeMap(hashMapOf(
                "mch_appid" to wxmpAppId,
                "mchid" to wxMchId,
                "nonce_str" to nonceStr,
                "partner_trade_no" to record.tradeNo,
                "openid" to record.receiverOpenId,
                "check_name" to "FORCE_CHECK",
                "re_user_name" to record.receiverName,
                "amount" to record.amount.toString(),
                "desc" to desc,
                "spbill_create_ip" to ipAddress
        ))

        val requestBody = getPaymentRequestParams(wxPayKey, params)

        val headers = HttpHeaders()
        headers.set(HttpHeaders.CONTENT_TYPE, "text/xml;charset=UTF-8")

        val request = HttpEntity<String>(requestBody, headers)
        val response = restTemplate.exchange(
                "https://api.mch.weixin.qq.com/mmpaymkttransfers/promotion/transfers",
                HttpMethod.POST,
                request,
                Resource::class.java)


        InputStreamReader(response.body.inputStream, StandardCharsets.UTF_8).use {
            val resultStr = String(response.body.inputStream.readBytes(), StandardCharsets.UTF_8)
            println(resultStr)

            val result = wxEnterprisePayResultUnmarshaller.unmarshal(StringReader(resultStr)) as WxEnterprisePayResult

            if (result.return_code == "SUCCESS" && result.result_code == "SUCCESS") {
                logger.info("Done WxEntTransfer for companyId=${record.companyId}, receiverOpenId=${record.receiverOpenId}, amount=${record.amount}")

                record.transferTime.time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").parse(result.payment_time)
                wxEntTransferRecordDao.save(record)

                return
            }
        }

        throw ProcessException(1, "Failed WxEntTransfer for companyId=${record.companyId}, receiverOpenId=${record.receiverOpenId}, amount=${record.amount}")
    }

    override fun printOrderStat(companyId: Int, startTime: Calendar, endTime: Calendar, positionId: Int, printStationId: Int): PrintOrderStatDTO {
        val printStationIds = ArrayList<Int>()
        if (printStationId > 0) {
            printStationIds.add(printStationId)
        }
        else if (positionId > 0) {
            printStationIds.addAll(
                    printStationDao.findByPositionId(positionId).map { it.id }
            )
        }


        return printOrderDao.printOrderStat(
                companyId = companyId,
                startTime = startTime,
                endTime = endTime,
                payed = true,
                printStationIds = printStationIds)
    }

    override fun queryPrinterOrders(pageNo: Int, pageSize: Int,
                                    companyId: Int,
                                    startTime: Calendar?, endTime: Calendar?,
                                    positionId: Int, printStationId: Int,
                                    order: String
    ): Page<PrintOrder> {

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

        val printStationIds = ArrayList<Int>()
        if (printStationId > 0) {
            printStationIds.add(printStationId)
        }
        else if (positionId > 0) {
            printStationIds.addAll(
                    printStationDao.findByPositionId(positionId).map { it.id }
            )
        }

        val pageReq = PageRequest(pageNo - 1, pageSize,
                Sort(Sort.Order(if (asc) Sort.Direction.ASC else Sort.Direction.DESC, orderField)))

        return printOrderDao.queryPrintOrders(pageReq, companyId, startTime, endTime, printStationIds)
    }

    override fun queryPrinterOrders(companyId: Int,
                                    startTime: Calendar?, endTime: Calendar?,
                                    positionId: Int, printStationId: Int,
                                    order: String
    ): List<PrintOrder> {

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

        val printStationIds = ArrayList<Int>()
        if (printStationId > 0) {
            printStationIds.add(printStationId)
        }
        else if (positionId > 0) {
            printStationIds.addAll(
                    printStationDao.findByPositionId(positionId).map { it.id }
            )
        }

        val sort = Sort(Sort.Order(if (asc) Sort.Direction.ASC else Sort.Direction.DESC, orderField))

        return printOrderDao.queryPrintOrders(sort, companyId, startTime, endTime, printStationIds)
    }
}


