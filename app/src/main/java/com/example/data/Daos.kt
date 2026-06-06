package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface UserDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<User>>

    @Query("SELECT * FROM users WHERE email = :email LIMIT 1")
    suspend fun getUser(email: String): User?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: User)

    @Update
    suspend fun updateUser(user: User)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products ORDER BY id DESC")
    fun getAllProducts(): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    fun getProductById(id: Int): Flow<Product?>

    @Query("SELECT * FROM products WHERE id = :id LIMIT 1")
    suspend fun getProductByIdSuspend(id: Int): Product?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Update
    suspend fun updateProduct(product: Product)

    @Delete
    suspend fun deleteProduct(product: Product)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteProductById(id: Int)
}

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category)

    @Delete
    suspend fun deleteCategory(category: Category)
}

@Dao
interface CartDao {
    @Query("SELECT * FROM cart_items WHERE userEmail = :email")
    fun getCartForUser(email: String): Flow<List<CartItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCartItem(item: CartItem)

    @Update
    suspend fun updateCartItem(item: CartItem)

    @Delete
    suspend fun deleteCartItem(item: CartItem)

    @Query("DELETE FROM cart_items WHERE userEmail = :email")
    suspend fun clearCartForUser(email: String)
}

@Dao
interface WishlistDao {
    @Query("SELECT * FROM wishlist_items WHERE userEmail = :email")
    fun getWishlistForUser(email: String): Flow<List<WishlistItem>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertWishlistItem(item: WishlistItem)

    @Query("DELETE FROM wishlist_items WHERE userEmail = :email AND productId = :productId")
    suspend fun deleteWishlistItem(email: String, productId: Int)
}

@Dao
interface OrderDao {
    @Query("SELECT * FROM orders ORDER BY date DESC")
    fun getAllOrders(): Flow<List<Order>>

    @Query("SELECT * FROM orders WHERE userEmail = :email ORDER BY date DESC")
    fun getOrdersForUser(email: String): Flow<List<Order>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order): Long

    @Update
    suspend fun updateOrder(order: Order)
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM reviews WHERE productId = :productId AND isApproved = 1 ORDER BY date DESC")
    fun getApprovedReviewsForProduct(productId: Int): Flow<List<Review>>

    @Query("SELECT * FROM reviews ORDER BY date DESC")
    fun getAllReviews(): Flow<List<Review>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReview(review: Review)

    @Update
    suspend fun updateReview(review: Review)

    @Delete
    suspend fun deleteReview(review: Review)
}

@Dao
interface CouponDao {
    @Query("SELECT * FROM coupons")
    fun getAllCoupons(): Flow<List<Coupon>>

    @Query("SELECT * FROM coupons WHERE code = :code LIMIT 1")
    suspend fun getCoupon(code: String): Coupon?

    @Query("DELETE FROM coupons WHERE code = :code")
    suspend fun deleteCoupon(code: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCoupon(coupon: Coupon)
}
