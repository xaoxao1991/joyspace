package com.unicolour.joyspace.controller.api

import com.unicolour.joyspace.dto.CommonRequestResult
import com.unicolour.joyspace.dto.ImageInfo
import com.unicolour.joyspace.service.ImageService
import com.unicolour.joyspace.util.getBaseUrl
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestMethod
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import javax.servlet.http.HttpServletRequest


@RestController
class ApiImageController {
    @Autowired
    lateinit var imageService: ImageService

    @RequestMapping("/api/image", method = arrayOf(RequestMethod.POST))
    fun uploadImage(request: HttpServletRequest,
                    @RequestParam("sessionId") sessionId: String,
                    @RequestParam("thumbMaxWidth") thumbMaxWidth: Int,
                    @RequestParam("thumbMaxHeight") thumbMaxHeight: Int,
                    @RequestParam("image") imgFile: MultipartFile?) : ResponseEntity<ImageInfo> {
        val baseUrl = getBaseUrl(request)
        val imgInfo = imageService.uploadImage(sessionId, thumbMaxWidth, thumbMaxHeight, imgFile, baseUrl)
        return ResponseEntity.ok(imgInfo)
    }

    @RequestMapping("/api/image", method = arrayOf(RequestMethod.DELETE))
    fun deleteImage(@RequestParam("sessionId") sessionId: String,
                    @RequestParam("imageId") imageId: Int) : ResponseEntity<CommonRequestResult> {
        val result = imageService.deleteImage(sessionId, imageId)
        return ResponseEntity.ok(result)
    }
}

