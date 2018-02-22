package com.unicolour.joyspace.service

import com.unicolour.joyspace.dto.*
import com.unicolour.joyspace.model.AdSet
import com.unicolour.joyspace.model.PrintStation
import com.unicolour.joyspace.model.PrintStationLoginSession
import graphql.schema.DataFetcher
import javax.transaction.Transactional

interface PrintStationService {
    fun getPriceMap(printStation: PrintStation): Map<Int, Int>
    fun createPrintStation(companyId: Int, printStationName: String, printStationPassword: String, positionId: Int, transferProportion:Int, printerType:String, adSetId: Int, selectedProductIds: Set<Int>): PrintStation?
    fun updatePrintStation(id: Int, companyId: Int, printStationName: String, printStationPassword: String, positionId: Int, transferProportion:Int, printerType:String, adSetId: Int, selectedProductIds: Set<Int>): Boolean

    fun activatePrintStation(code: String, name:String, password: String, positionId: Int, selectedProductIds: Set<Int>)

    val loginDataFetcher: DataFetcher<PrintStationLoginResult>
    val printStationDataFetcher: DataFetcher<PrintStation>
    val nearestDataFetcher: DataFetcher<PrintStationFindResultSingle>
    val byCityDataFetcher: DataFetcher<PrintStationFindResult>

    val byDistanceDataFetcher: DataFetcher<PrintStationFindResult>
    val newAdSetDataFetcher: DataFetcher<AdSet?>

    val currentSoftwareVersionDataFetcher: DataFetcher<Int>
    fun getDataFetcher(fieldName:String): DataFetcher<Any>
    fun getPrintStationLoginSession(sessionId: String): PrintStationLoginSession?

    fun getPrintStationUrl(printStationId: Int): String
}