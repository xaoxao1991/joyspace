package com.unicolour.joyspace.controller.api.v2

import com.unicolour.joyspace.dao.PrintOrderDao
import com.unicolour.joyspace.dao.UserAddressDao
import com.unicolour.joyspace.dao.UserDao
import com.unicolour.joyspace.dao.UserLoginSessionDao
import com.unicolour.joyspace.dto.NoticeVo
import com.unicolour.joyspace.dto.ResultCode
import com.unicolour.joyspace.dto.UserInfoVo
import com.unicolour.joyspace.dto.common.RestResponse
import com.unicolour.joyspace.model.Address
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.util.*

@RestController
class ApiUserRoute {

    val logger = LoggerFactory.getLogger(this.javaClass)
    @Autowired
    private lateinit var userAddressDao: UserAddressDao
    @Autowired
    private lateinit var userLoginSessionDao: UserLoginSessionDao
    @Autowired
    private lateinit var userDao: UserDao
    @Autowired
    private lateinit var printOrderDao: PrintOrderDao

    //获取个人信息
    @GetMapping(value = "/v2/user/info")
    fun getUserInfo(@RequestParam("sessionId") sessionId: String): RestResponse {
        val session = userLoginSessionDao.findOne(sessionId)
        val user = userDao.findOne(session.userId) ?: return RestResponse.error(ResultCode.INVALID_USER_LOGIN_SESSION)
        val userInfo = UserInfoVo()
        userInfo.nickName = user.nickName
        userInfo.imageUrl = user.avatar
        userInfo.unPayCount = printOrderDao.countByUserIdAndPayedIsFalse(user.id).toInt()
        userInfo.handlingCount = printOrderDao.countByUserIdAndPayedIsTrueAndPrintedOnPrintStationIsFalse(user.id).toInt()
        return RestResponse.ok(userInfo)
    }

    //消息中心list
    @GetMapping(value = "/v2/user/notice")
    fun getUserNotice(@RequestParam("sessionId") sessionId: String): RestResponse {
        val session = userLoginSessionDao.findOne(sessionId)
        userDao.findOne(session.userId) ?: return RestResponse.error(ResultCode.INVALID_USER_LOGIN_SESSION)
        val noticeList = mutableListOf<NoticeVo>()
        noticeList.add(NoticeVo("通知","快来看看这些精美的手工日记吧，所有照片。。。",Date(),""))
        noticeList.add(NoticeVo("活动消息","情人节，悦印送您优惠券啦，除了送花还可以。。。",Date(),""))
        return RestResponse.ok(noticeList)
    }

    /**
     * 获取用户地址列表
     */
    @GetMapping(value = "/v2/user/address")
    fun listAddress(@RequestParam("sessionId") sessionId: String): RestResponse {
        val session = userLoginSessionDao.findOne(sessionId)
        val user = userDao.findOne(session.userId) ?: return RestResponse.error(ResultCode.INVALID_USER_LOGIN_SESSION)
        val addressList = userAddressDao.findByUserId(user.id)
        return RestResponse.ok(mapOf("addressList" to addressList))
    }


    /**
     * 用户新增地址
     */
    @PostMapping(value = "/v2/user/address")
    fun add(@RequestParam("sessionId") sessionId: String,
            @RequestParam("province") province: String,
            @RequestParam("city") city: String,
            @RequestParam("area") area: String,
            @RequestParam("address") address: String,
            @RequestParam("phoneNum") phoneNum: String,
            @RequestParam("name") name: String,
            @RequestParam("default") default: Int?): RestResponse {
        val session = userLoginSessionDao.findOne(sessionId)
        val user = userDao.findOne(session.userId) ?: return RestResponse.error(ResultCode.INVALID_USER_LOGIN_SESSION)
        val addr = Address()
        addr.userId = user.id
        addr.province = province
        addr.city = city
        addr.area = area
        addr.address = address
        addr.phoneNum = phoneNum
        addr.name = name
        if (default == 1) addr.defalut = true
        addr.createTime = Calendar.getInstance()
        userAddressDao.save(addr)
        return RestResponse.ok()
    }
}