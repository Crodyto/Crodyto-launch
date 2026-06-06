package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "users")
data class User(
    @PrimaryKey val email: String,
    val name: String,
    val role: String = "user", // "user" or "admin"
    val isBlocked: Boolean = false
)

@Entity(tableName = "products")
data class Product(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val description: String,
    val price: Double,
    val category: String,
    val rating: Double = 4.0,
    val imageUrl: String = "",
    val stock: Int = 10
)

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey val name: String,
    val imageUrl: String = ""
)

@Entity(tableName = "cart_items")
data class CartItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val productId: Int,
    val quantity: Int
)

@Entity(tableName = "wishlist_items")
data class WishlistItem(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val productId: Int
)

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val userEmail: String,
    val orderItemsStr: String, // format: "productId:quantity;productId:quantity"
    val address: String,
    val paymentMethod: String, // "UPI", "COD"
    val paymentStatus: String, // "Pending", "Completed"
    val orderStatus: String, // "Pending", "Dispatched", "Out for Delivery", "Delivered"
    val totalAmount: Double,
    val date: Long = System.currentTimeMillis()
)

@Entity(tableName = "reviews")
data class Review(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val productId: Int,
    val userEmail: String,
    val username: String,
    val rating: Int,
    val comment: String,
    val date: Long = System.currentTimeMillis(),
    val isApproved: Boolean = false // Admin review moderation
)

@Entity(tableName = "coupons")
data class Coupon(
    @PrimaryKey val code: String,
    val discountPercent: Int, // e.g. 15 for 15%
    val isActive: Boolean = true
)
