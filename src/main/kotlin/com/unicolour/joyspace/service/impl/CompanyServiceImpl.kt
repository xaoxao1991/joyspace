package com.unicolour.joyspace.service.impl

import com.unicolour.joyspace.dao.CompanyDao
import com.unicolour.joyspace.dao.CompanyWxAccountDao
import com.unicolour.joyspace.dao.WeiXinPayConfigDao
import com.unicolour.joyspace.dao.WxEntTransferRecordDao
import com.unicolour.joyspace.dto.ResultCode
import com.unicolour.joyspace.exception.ProcessException
import com.unicolour.joyspace.model.Company
import com.unicolour.joyspace.model.CompanyWxAccount
import com.unicolour.joyspace.model.PriceList
import com.unicolour.joyspace.service.CompanyService
import com.unicolour.joyspace.service.ManagerService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service
import java.util.*
import javax.transaction.Transactional

@Service
open class CompanyServiceImpl : CompanyService {
    @Autowired
    lateinit var companyDao: CompanyDao

    @Autowired
    lateinit var weiXinPayConfigDao: WeiXinPayConfigDao

    @Autowired
    lateinit var companyWxAccountDao: CompanyWxAccountDao

    @Autowired
    lateinit var wxEntTransferRecordDao: WxEntTransferRecordDao

    @Autowired
    lateinit var managerService: ManagerService

    @Transactional
    override fun createCompany(name: String, defPriceList: PriceList?,
                               username: String,
                               fullname: String,
                               phone: String,
                               email: String,
                               password: String): Company {
        if (companyDao.existsByNameIgnoreCase(name)) {
            throw ProcessException(ResultCode.COMPANY_ALREADY_EXISTS, "名称为${name}的投放商已存在")
        }

        val company = Company()

        company.name = name
        company.createTime = Calendar.getInstance()
        company.defaultPriceList = defPriceList
        company.weiXinPayConfig = null

        companyDao.save(company)

        var roles = "ADMIN"
        if (username == "admin") {
            roles = "SUPERADMIN,ADMIN"
        }

        managerService.createManager(username, password, fullname, phone, email, roles, company)

        return company
    }

    override fun updateCompany(companyId: Int, name: String): Boolean {
        val company = companyDao.findOne(companyId)
        if (company != null) {
            company.name = name
            companyDao.save(company)

            return true
        }
        else {
            return false
        }
    }

    private fun getTransferCountOfOpenId(openId: String): Long {
        val startOfToday = Calendar.getInstance()
        startOfToday.set(Calendar.HOUR_OF_DAY, 0)
        startOfToday.set(Calendar.MINUTE, 0)
        startOfToday.set(Calendar.SECOND, 0)
        startOfToday.set(Calendar.MILLISECOND, 0)

        return wxEntTransferRecordDao.countByReceiverOpenIdAndTransferTimeAfter(openId, startOfToday)
    }

    override fun getAvailableWxAccount(companyId: Int): CompanyWxAccount? {
        val accounts = companyWxAccountDao.findByCompanyId(companyId)
        if (!accounts.isEmpty()) {
            for (account in accounts) {
                if (account.enabled) {
                    val transferCount = getTransferCountOfOpenId(account.openId)
                    if (transferCount < 99) {
                        return account
                    }
                }
            }
        }

        return null
    }
}
