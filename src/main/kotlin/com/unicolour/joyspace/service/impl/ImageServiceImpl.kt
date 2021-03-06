package com.unicolour.joyspace.service.impl

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import com.unicolour.joyspace.dao.ProductDao
import com.unicolour.joyspace.dao.UserImageFileDao
import com.unicolour.joyspace.dao.UserLoginSessionDao
import com.unicolour.joyspace.dto.*
import com.unicolour.joyspace.exception.ProcessException
import com.unicolour.joyspace.model.UserImageFile
import com.unicolour.joyspace.service.ImageService
import graphql.schema.DataFetcher
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import org.springframework.boot.web.servlet.MultipartConfigFactory
import org.springframework.context.annotation.Bean
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.*
import java.util.regex.Pattern
import javax.servlet.MultipartConfigElement

@Service
class ImageServiceImpl : ImageService {

    companion object {
        val logger = LoggerFactory.getLogger(PositionServiceImpl::class.java)
    }

    @Value("\${com.unicolour.joyspace.baseUrl}")
    lateinit var baseUrl: String

    @Value("\${com.unicolour.joyspace.assetsDir}")
    lateinit var assetsDir: String

    @Autowired
    lateinit var userImageFileDao: UserImageFileDao

    @Autowired
    lateinit var userLoginSessionDao: UserLoginSessionDao

    @Autowired
    lateinit var productDao: ProductDao

    @Autowired
    lateinit var objectMapper: ObjectMapper

    override fun uploadImage(filterImageId: String?,sessionId: String, imgFile: MultipartFile?): ImageInfo {
        val session = userLoginSessionDao.findOne(sessionId);

        if (session == null) {
            return ImageInfo(1, "用户未登录")
        } else if (imgFile == null) {
            return ImageInfo(2, "没有图片文件")
        } else {
            try {

                val fileName = UUID.randomUUID().toString().replace("-", "")
                val filePath : String?
                val file : File?
                if (filterImageId != null) {
                    filePath = "filterTmp/order/${session.userId}/${fileName}.jpg"
                    file = File(assetsDir, filePath)
                    file.parentFile.mkdirs()
                    imgFile.transferTo(file)
                } else {
                    filePath = "user/${session.userId}/${sessionId}/${fileName}"
                    file = File(assetsDir, filePath)
                    file.parentFile.mkdirs()
                }


                logger.info("file.absolutePath:${file.absolutePath},fileName=${fileName}")

                val pb = ProcessBuilder("magick", "identify", file.absolutePath)

                val process = pb.start()

                var retStr: String = "";
                BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                    retStr = reader.readText()
                }


                val retCode = process.waitFor()

                if (retCode != 0) {
                    logger.error("图片处理失败，retStr:$retStr , retCode: $retCode")
                    return ImageInfo(3, retStr)
                } else {
                    val patternStr = Pattern.quote(file.absolutePath) + "\\s(\\w+)\\s(\\d+)x(\\d+)\\s.*"
                    val pattern = Pattern.compile(patternStr)
                    val matcher = pattern.matcher(retStr)

                    matcher.find()

                    var imgType = matcher.group(1).toLowerCase()
                    if (imgType == "jpeg") {
                        imgType = "jpg"
                    }
                    val imgWid = matcher.group(2).toInt()
                    val imgHei = matcher.group(3).toInt()



                    val fileWithExt = File(assetsDir, "${filePath.split(".").first()}.${imgType}")
                    file.renameTo(fileWithExt)

                    logger.info("fileWithExt=" + fileWithExt)

                    val fileWithExtOut : File?
                    if (filterImageId != null) {
                        fileWithExtOut = File(assetsDir, "${filePath.split(".").first()}_${filterImageId}.${imgType}")
                    } else {
                        fileWithExtOut = File(assetsDir,"${filePath}.${imgType}")
                    }


                    logger.info("fileWithExtOut=" + fileWithExtOut)


                    val processBuilder = ProcessBuilder("/root/miniconda3/bin/python","/root/joy_style/joy_api.py",fileWithExt.absolutePath,fileWithExtOut.absolutePath,filterImageId).start()


                    var retStr: String = "";
                    BufferedReader(InputStreamReader(processBuilder.inputStream)).use { reader ->
                        retStr = reader.readText()
                    }

                    val retCode = process.waitFor()

                    if (retCode != 0) {
                        logger.error("下单生成滤镜失败，retStr:$retStr , retCode: $retCode")
                        return ImageInfo(3, retStr)
                    }



                    val url :String
                    if (filterImageId != null) {
                        url = "${baseUrl}/assets/${filePath.split(".").first()}_${filterImageId}.${imgType}"
                    } else {
                        url = "${baseUrl}/assets/${filePath.split(".").first()}.${imgType}"
                    }
                    logger.info("uploadImage url=" + url)
                    val userImgFile = UserImageFile()
                    userImgFile.type = imgType
                    userImgFile.fileName = fileName
                    userImgFile.width = imgWid
                    userImgFile.height = imgHei
                    userImgFile.sessionId = sessionId
                    userImgFile.uploadTime = Calendar.getInstance()
                    userImgFile.userId = session.userId


                    userImageFileDao.save(userImgFile)

                    return ImageInfo(0, null, userImgFile.id, imgWid, imgHei, url)
                }
            } catch (e: Exception) {
                logger.error("error occurs while ", e)
                return ImageInfo(3, "")
            }
        }
    }

    override fun createThumbnail(sessionId: String, userImgFile: UserImageFile, width: Int, height: Int): UserImageFile? {
        val destFileName = UUID.randomUUID().toString().replace("-", "")
        val destFilePath = "user/${userImgFile.userId}/${sessionId}/${destFileName}.jpg"

        val destFile = File(assetsDir, destFilePath)

        val srcFilePath = "user/${userImgFile.userId}/${userImgFile.sessionId}/${userImgFile.fileName}.${userImgFile.type}"
        val srcFile = File(assetsDir, srcFilePath)

        val pb = ProcessBuilder(
                "magick",
                "convert",
                srcFile.absolutePath,
                "-thumbnail",
                "${width}x${height}",
                "-identify",
                destFile.absolutePath)

        val process = pb.start()

        var retStr: String = "";
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            retStr = reader.readText()
        }

        val retCode = process.waitFor()

        if (retCode != 0) {
            return null
        } else {
            val patternStr = Pattern.quote(srcFile.absolutePath) + "\\s\\w+\\s\\d+x\\d+=>(\\d+)x(\\d+).*"
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(retStr)

            matcher.find()

            val destImgWid = matcher.group(1).toInt()
            val destImgHei = matcher.group(2).toInt()

            val newUserImgFile = UserImageFile()
            newUserImgFile.type = "jpg"
            newUserImgFile.fileName = destFileName
            newUserImgFile.width = destImgWid
            newUserImgFile.height = destImgHei
            newUserImgFile.sessionId = sessionId
            newUserImgFile.uploadTime = Calendar.getInstance()
            newUserImgFile.userId = userImgFile.userId
            userImageFileDao.save(newUserImgFile)

            userImgFile.thumbnail = newUserImgFile
            userImageFileDao.save(userImgFile)

            return newUserImgFile
        }
    }

    override fun deleteImage(sessionId: String, imageId: Int): CommonRequestResult {
        val session = userLoginSessionDao.findOne(sessionId);

        if (session == null) {
            return CommonRequestResult(1, "用户未登录")
        } else {
            val imageFile = userImageFileDao.findOne(imageId);
            if (imageFile == null) {
                return CommonRequestResult(2, "图片不存在")
            } else if (imageFile.userId != session.userId) {
                return CommonRequestResult(3, "图片不属于当前登录用户")
            } else {
                val filePath = "user/${imageFile.userId}/${imageFile.sessionId}/${imageFile.fileName}"
                val file = File(assetsDir, "${filePath}/.${imageFile.type}")
                val thumbFile = File(assetsDir, "${filePath}.thumb.jpg")

                file.delete()
                thumbFile.delete()

                userImageFileDao.delete(imageFile)

                return CommonRequestResult(0, null)
            }
        }
    }

    override fun getImageFileUrlDataFetcher(): DataFetcher<String> {
        return DataFetcher { env ->
            val imageFile = env.getSource<UserImageFile>()
            "${baseUrl}/assets/user/${imageFile.userId}/${imageFile.sessionId}/${imageFile.fileName}.${imageFile.type}"
        }
    }

    override fun getImageUrl(userImgFile: UserImageFile): String {
        return "${baseUrl}/assets/user/${userImgFile.userId}/${userImgFile.sessionId}/${userImgFile.fileName}.${userImgFile.type}"
    }

    override fun getImageFileUrl(userImgFile: UserImageFile): String {
        val file = File(assetsDir, "/user/${userImgFile.userId}/${userImgFile.sessionId}/${userImgFile.fileName}.${userImgFile.type}")
        return file.toURI().toURL().toExternalForm()
    }

    override fun getImageFileDimensionAndType(imageFile: File): ImageFileDimensionAndType {
        val pb = ProcessBuilder("magick", "identify", imageFile.absolutePath)

        val process = pb.start()

        var retStr = ""
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            retStr = reader.readText()
        }

        val retCode = process.waitFor()

        if (retCode != 0) {
            imageFile.delete()
            throw IOException("not valid image file")
        } else {
            val patternStr = Pattern.quote(imageFile.absolutePath) + "\\s(\\w+)\\s(\\d+)x(\\d+)\\s.*"
            val pattern = Pattern.compile(patternStr)
            val matcher = pattern.matcher(retStr)

            matcher.find()

            var imgType = matcher.group(1).toLowerCase()
            if (imgType == "jpeg") {
                imgType = "jpg"
            }
            val imgWid = matcher.group(2).toInt()
            val imgHei = matcher.group(3).toInt()

            return ImageFileDimensionAndType(width = imgWid, height = imgHei, type = imgType)
        }
    }

    override fun createThumbnailImageFile(srcImgFile: File, geometry: String, thumbImgFile: File) {
        val pb = ProcessBuilder("magick", "convert", "-resize", geometry, srcImgFile.absolutePath, thumbImgFile.absolutePath)
        val process = pb.start()
        process.waitFor()
    }

    /**
     * 调用python,获取滤镜风格列表
     */
    override fun filterImageList(sessionId: String): List<Filter> {
        userLoginSessionDao.findOne(sessionId) ?: throw ProcessException(1, "用户未登录")
        val filePath = "filter/filterList.json"
        val desImage = File(assetsDir, filePath)
        desImage.parentFile.mkdirs()
        logger.info("uploadFilterImage desImage:$desImage")
        getFilterListJsonFile(desImage)
        val jsonFile = File(desImage.absoluteFile.toString())
        if (jsonFile.exists()) {
            logger.info("强转")
            return objectMapper.readValue(jsonFile, object : TypeReference<List<Filter>>() {})
        } else {
            throw ProcessException(2, "没找到 json 文件")
        }


    }

    private fun getFilterListJsonFile(desImage: File) {
        val process = ProcessBuilder("/root/miniconda3/bin/python", "/root/joy_style/joy_api.py", desImage.absolutePath).start()
        var retStr = ""
        var retError = ""
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            retStr = reader.readText()
        }
        BufferedReader(InputStreamReader(process.errorStream)).use { reader ->
            retError = reader.readText()
        }
        val retCode = process.waitFor()
        logger.info("filterImageList retStr:$retStr,retCode:$retCode,retError:$retError")
    }

    private fun getFilterImage(srcImgFile: File,filterImageId: Int) {
        logger.info("getFilterImage filterImageId=" + filterImageId)
        val desImagePath = srcImgFile.absolutePath.split(".").first().plus("_$filterImageId.").plus(srcImgFile.absolutePath.split(".").last())
        logger.info("desImagePath=" + desImagePath + "\tinputImagePath=" + srcImgFile)
        val imageToFilter = ProcessBuilder("/root/miniconda3/bin/python","/root/joy_style/joy_api.py",srcImgFile.absolutePath.toString(),desImagePath,filterImageId.toString()).start()
        var retStr = ""
        var retError = ""
        BufferedReader(InputStreamReader(imageToFilter.inputStream)).use { reader ->
            retStr = reader.readText()
        }
        BufferedReader(InputStreamReader(imageToFilter.errorStream)).use { reader ->
            retError = reader.readText()
        }
        val retCode = imageToFilter.waitFor()
        logger.info("getFilterImage retStr:$retStr,retCode:$retCode,retError:$retError")
    }

    /**
     * 根据前段传过来的图片生成效果滤镜图片
     */
    override fun imageToFilter(sessionId: String, imgFile: MultipartFile?):String {
        var session = userLoginSessionDao.findOne(sessionId) ?: throw ProcessException(1, "用户未登录")
        imgFile ?: throw ProcessException(2, "没有图片文件")
        val fileName = UUID.randomUUID().toString().replace("-", "")
        val filePath = "filterTmp/${session.userId}/$fileName.jpg"
        val srcImgFile = File(assetsDir, filePath)
        srcImgFile.parentFile.mkdirs()
        imgFile.transferTo(srcImgFile)
        val jsonFile = File(assetsDir,"filter/filterList.json")
        if (!jsonFile.exists()) throw ProcessException(3,"没找到filterList.json文件")
        val filterList: List<Filter> = objectMapper.readValue(jsonFile, object : TypeReference<List<Filter>>() {})
        logger.info("filterListInfo=" + filterList)
        filterList.forEach {
            getFilterImage(srcImgFile,it.id)
        }
        val url = filePath.split(".").first() + "_##filterId##.jpg"
        logger.info("imageToFilter url=" + url)
        return "$baseUrl/assets/$url"
    }

}