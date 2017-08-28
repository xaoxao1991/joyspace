package com.unicolour.joyspace.model

import javax.persistence.*
import javax.validation.constraints.NotNull

/** 产品 */
@Entity
@Table(name = "product")
class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int = 0

    @Column(length = 50)
    @NotNull
    var name: String = ""

    @Column(length = 50)
    @NotNull
    var templateName: String = ""

    /** 类型 */
    @Column
    @NotNull
    var type: Int = ProductType.PHOTO.value;

    /** 产品宽度(mm) */
    @Column
    @NotNull
    var width: Double = 0.0;

    /** 产品高度(mm) */
    @Column
    @NotNull
    var height: Double = 0.0;

    /** 最小图片数量 */
    @Column
    @NotNull
    var minImageCount: Int = 0;

    @Column
    @NotNull
    var enabled: Boolean = false

    /** 备注 */
    @Column(length = 255)
    var remark: String? = null

    /** 缺省价格(单位是分) */
    @Column
    @NotNull
    var defaultPrice: Int = 0

    @OneToMany(mappedBy = "product")
    lateinit var imageFiles: List<ProductImageFile>
}

/** 产品类别 */
enum class ProductType(val value:Int, val dispName:String) {
    PHOTO(0, "普通照片"),
    ID_PHOTO(1, "证件照"),
    TEMPLATE(2, "模板拼图");
}


/** 产品图片 */
@Entity
@Table(name = "product_image_file")
class ProductImageFile {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Int = 0

    /** 类型 0: thumb  1: preview */
    @Column
    @NotNull
    var type: Int = ProductImageFileType.THUMB.value

    /** 文件类型 jpg, png ... */
    @NotNull
    @Column(length = 10)
    lateinit var fileType: String

    /** 属于哪个产品 */
    @Column(name = "product_id", insertable = false, updatable = false)
    var productId: Int = 0

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id")
    @NotNull
    lateinit var product: Product
}

/** 产品图片类别 */
enum class ProductImageFileType(var value: Int) {
    THUMB(0),
    PREVIEW(1)
}
