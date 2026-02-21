package com.loopers.domain.product

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(name = "tb_product_inventory")
class ProductInventoryModel(
    productId: Long,
    stock: Stock
) {

    @Id
    val productId: Long = productId

    @Column
    var stock: Stock = stock
        protected set

    fun increaseStock(quantity: Long) {
        stock = Stock(stock.value + quantity)
    }

    fun decreaseStock(quantity: Long) {
        require(stock.value >= quantity) { "재고가 부족합니다." }
        stock = Stock(stock.value - quantity)
    }
}
