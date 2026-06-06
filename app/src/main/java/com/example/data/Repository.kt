package com.example.data

import kotlinx.coroutines.flow.Flow

class CrodytoRepository(
    private val userDao: UserDao,
    private val productDao: ProductDao,
    private val categoryDao: CategoryDao,
    private val cartDao: CartDao,
    private val wishlistDao: WishlistDao,
    private val orderDao: OrderDao,
    private val reviewDao: ReviewDao,
    private val couponDao: CouponDao
) {

    // User Operations
    val allUsers: Flow<List<User>> = userDao.getAllUsers()
    
    suspend fun getUser(email: String): User? = userDao.getUser(email)
    
    suspend fun insertUser(user: User) = userDao.insertUser(user)
    
    suspend fun updateUser(user: User) = userDao.updateUser(user)

    // Product Operations
    val allProducts: Flow<List<Product>> = productDao.getAllProducts()
    
    fun getProductById(id: Int): Flow<Product?> = productDao.getProductById(id)
    
    suspend fun getProductByIdSuspend(id: Int): Product? = productDao.getProductByIdSuspend(id)
    
    suspend fun insertProduct(product: Product) = productDao.insertProduct(product)
    
    suspend fun updateProduct(product: Product) = productDao.updateProduct(product)
    
    suspend fun deleteProduct(product: Product) = productDao.deleteProduct(product)
    
    suspend fun deleteProductById(id: Int) = productDao.deleteProductById(id)

    // Category Operations
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    
    suspend fun insertCategory(category: Category) = categoryDao.insertCategory(category)
    
    suspend fun deleteCategory(category: Category) = categoryDao.deleteCategory(category)

    // Cart Operations
    fun getCartForUser(email: String): Flow<List<CartItem>> = cartDao.getCartForUser(email)
    
    suspend fun insertCartItem(item: CartItem) = cartDao.insertCartItem(item)
    
    suspend fun updateCartItem(item: CartItem) = cartDao.updateCartItem(item)
    
    suspend fun deleteCartItem(item: CartItem) = cartDao.deleteCartItem(item)
    
    suspend fun clearCartForUser(email: String) = cartDao.clearCartForUser(email)

    // Wishlist Operations
    fun getWishlistForUser(email: String): Flow<List<WishlistItem>> = wishlistDao.getWishlistForUser(email)
    
    suspend fun insertWishlistItem(item: WishlistItem) = wishlistDao.insertWishlistItem(item)
    
    suspend fun deleteWishlistItem(email: String, productId: Int) = wishlistDao.deleteWishlistItem(email, productId)

    // Order Operations
    val allOrders: Flow<List<Order>> = orderDao.getAllOrders()
    
    fun getOrdersForUser(email: String): Flow<List<Order>> = orderDao.getOrdersForUser(email)
    
    suspend fun insertOrder(order: Order): Long = orderDao.insertOrder(order)
    
    suspend fun updateOrder(order: Order) = orderDao.updateOrder(order)

    // Review Operations
    val allReviews: Flow<List<Review>> = reviewDao.getAllReviews()
    
    fun getApprovedReviewsForProduct(productId: Int): Flow<List<Review>> = reviewDao.getApprovedReviewsForProduct(productId)
    
    suspend fun insertReview(review: Review) = reviewDao.insertReview(review)
    
    suspend fun updateReview(review: Review) = reviewDao.updateReview(review)
    
    suspend fun deleteReview(review: Review) = reviewDao.deleteReview(review)

    // Coupon Operations
    val allCoupons: Flow<List<Coupon>> = couponDao.getAllCoupons()
    
    suspend fun getCoupon(code: String): Coupon? = couponDao.getCoupon(code)
    
    suspend fun insertCoupon(coupon: Coupon) = couponDao.insertCoupon(coupon)
    
    suspend fun deleteCoupon(code: String) = couponDao.deleteCoupon(code)
}
