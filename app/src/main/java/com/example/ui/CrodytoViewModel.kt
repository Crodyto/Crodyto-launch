package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class CrodytoViewModel(private val repository: CrodytoRepository) : ViewModel() {

    // Seeding Status
    val isSeeding = MutableStateFlow(true)

    // Auth States
    private val _loggedInUser = MutableStateFlow<User?>(null)
    val loggedInUser: StateFlow<User?> = _loggedInUser.asStateFlow()

    private val _authError = MutableStateFlow<String?>(null)
    val authError: StateFlow<String?> = _authError.asStateFlow()

    val usersList: StateFlow<List<User>> = repository.allUsers
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Product States
    val categories: StateFlow<List<Category>> = repository.allCategories
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val searchQuery = MutableStateFlow("")
    val selectedCategory = MutableStateFlow("")
    val maxPrice = MutableStateFlow(500f)
    val minRating = MutableStateFlow(0f)
    val sortOrder = MutableStateFlow("Default") // "Default", "Price Low-High", "Price High-Low", "Rating"

    val filteredProducts: StateFlow<List<Product>> = combine(
        repository.allProducts,
        searchQuery,
        selectedCategory,
        maxPrice,
        minRating,
        sortOrder
    ) { array ->
        @Suppress("UNCHECKED_CAST")
        val products = array[0] as List<Product>
        val query = array[1] as String
        val cat = array[2] as String
        val price = array[3] as Float
        val rating = array[4] as Float
        val sort = array[5] as String

        var list = products
        if (query.isNotEmpty()) {
            list = list.filter {
                it.name.contains(query, ignoreCase = true) ||
                        it.description.contains(query, ignoreCase = true)
            }
        }
        if (cat.isNotEmpty()) {
            list = list.filter { it.category == cat }
        }
        list = list.filter { it.price <= price }
        list = list.filter { it.rating >= rating }
        when (sort) {
            "Price Low-High" -> list = list.sortedBy { it.price }
            "Price High-Low" -> list = list.sortedByDescending { it.price }
            "Rating" -> list = list.sortedByDescending { it.rating }
            else -> list = list.sortedByDescending { it.id }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Cart and Wishlist
    val cartItems: StateFlow<List<CartItem>> = _loggedInUser.flatMapLatest { user ->
        if (user != null) {
            repository.getCartForUser(user.email)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wishlistItems: StateFlow<List<WishlistItem>> = _loggedInUser.flatMapLatest { user ->
        if (user != null) {
            repository.getWishlistForUser(user.email)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Enriched Cart (cart item + product)
    val enrichedCart: StateFlow<List<EnrichedCartItem>> = combine(
        cartItems,
        repository.allProducts
    ) { items, products ->
        items.mapNotNull { item ->
            val product = products.find { it.id == item.productId }
            if (product != null) {
                EnrichedCartItem(item, product)
            } else {
                null
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val cartSubtotal: StateFlow<Double> = enrichedCart.map { list ->
        list.sumOf { it.product.price * it.cartItem.quantity }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0.0)

    // Wishlist Product ids
    val wishlistProductIds: StateFlow<Set<Int>> = wishlistItems.map { list ->
        list.map { it.productId }.toSet()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    // Coupons
    val coupons: StateFlow<List<Coupon>> = repository.allCoupons
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val appliedCoupon = MutableStateFlow<Coupon?>(null)
    val couponInput = MutableStateFlow("")
    val couponMessage = MutableStateFlow<String?>(null)

    // Checkout Info
    val shippingAddress = MutableStateFlow("")
    val paymentMethod = MutableStateFlow("COD") // "UPI" or "COD"
    val checkoutStep = MutableStateFlow(1) // 1: Shipping, 2: Payment/Review, 3: Success

    // Active User Orders
    val userOrders: StateFlow<List<Order>> = _loggedInUser.flatMapLatest { user ->
        if (user != null) {
            repository.getOrdersForUser(user.email)
        } else {
            flowOf(emptyList())
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Admin Dashboard States
    val adminAllOrders: StateFlow<List<Order>> = repository.allOrders
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val adminAllReviews: StateFlow<List<Review>> = repository.allReviews
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        seedDatabase()
    }

    private fun seedDatabase() {
        viewModelScope.launch {
            isSeeding.value = true
            val products = repository.allProducts.firstOrNull() ?: emptyList()
            if (products.isEmpty()) {
                // Insert categories
                repository.insertCategory(Category("Electronics", ""))
                repository.insertCategory(Category("Fashion", ""))
                repository.insertCategory(Category("Footwear", ""))
                repository.insertCategory(Category("Accessories", ""))
                repository.insertCategory(Category("Home & Living", ""))

                // Seed Default Users
                repository.insertUser(User("admin@crodyto.com", "Director Admin", "admin"))
                repository.insertUser(User("user@crodyto.com", "Jane Doe", "user"))

                // Seed Coupons
                repository.insertCoupon(Coupon("WELCOME10", 10, true))
                repository.insertCoupon(Coupon("CRODYTO30", 30, true))
                repository.insertCoupon(Coupon("SUPER50", 50, true))

                // Seed Products
                repository.insertProduct(Product(name = "SoundMax ANC Wireless Headphones", description = "Ultra-fidelity immersive noise cancelling over-ear headphones with 40-hour battery life.", price = 199.99, category = "Electronics", rating = 4.8, imageUrl = "", stock = 12))
                repository.insertProduct(Product(name = "AeroCharge Wireless Dock", description = "3-in-1 fast chargers docking station for phones, watches, and wireless audio blocks.", price = 49.99, category = "Electronics", rating = 4.5, imageUrl = "", stock = 20))
                repository.insertProduct(Product(name = "Elite Fit Black Leather Jacket", description = "Premium hand-crafted full-grain leather bomber jacket with tailored metallic hardware.", price = 249.00, category = "Fashion", rating = 4.7, imageUrl = "", stock = 4))
                repository.insertProduct(Product(name = "SleekFit Urban Navy Blazer", description = "Classic double-breasted modern tailoring jacket for high-end professional presence.", price = 120.00, category = "Fashion", rating = 4.4, imageUrl = "", stock = 2))
                repository.insertProduct(Product(name = "Nimbus Run Air Sneakers", description = "Cloud-like responsive sole running sneakers designed for maximum athletic performance.", price = 139.50, category = "Footwear", rating = 4.6, imageUrl = "", stock = 15))
                repository.insertProduct(Product(name = "Classic Tan Chelsea Boots", description = "Durable slip-on water-resistant suede chelsea boots with heavily cushioned lining.", price = 159.00, category = "Footwear", rating = 4.2, imageUrl = "", stock = 6))
                repository.insertProduct(Product(name = "ChronoLux Onyx Analog Watch", description = "Minimalist stealth black premium analog timepiece with anti-scratch sapphire glass.", price = 299.00, category = "Accessories", rating = 4.9, imageUrl = "", stock = 3))
                repository.insertProduct(Product(name = "Voyage RFID Secure Wallet", description = "Bi-fold security shield wallet with quick modern card ejector action.", price = 39.99, category = "Accessories", rating = 4.5, imageUrl = "", stock = 50))
                repository.insertProduct(Product(name = "AromaDiffuser Ultrasonic Humidifier", description = "Whisper-quiet ultrasonic oil diffuser with multi-mode ambient lighting rings.", price = 34.99, category = "Home & Living", rating = 4.3, imageUrl = "", stock = 22))
                repository.insertProduct(Product(name = "SmartAmbient Interactive Lightbars", description = "Accent smart LED lightbars that sync dynamically with screen soundscapes.", price = 89.00, category = "Home & Living", rating = 4.6, imageUrl = "", stock = 8))

                // Seed Reviews
                repository.insertReview(Review(productId = 1, userEmail = "buyer@test.com", username = "Alex Rivera", rating = 5, comment = "Stunning ANC quality! Best headphones I have ever bought.", isApproved = true))
                repository.insertReview(Review(productId = 1, userEmail = "sam@test.com", username = "Sam Rogers", rating = 4, comment = "Very solid audio performance, but earcups are slightly tight on long use.", isApproved = true))
                repository.insertReview(Review(productId = 3, userEmail = "buyer@test.com", username = "Alex Rivera", rating = 5, comment = "Amazing leather tailoring, premium weight. Smells incredible too!", isApproved = true))
                repository.insertReview(Review(productId = 7, userEmail = "claire@test.com", username = "Claire Bennet", rating = 5, comment = "Exquisite design. Looks super classy and minimal on my wrist.", isApproved = true))
                // Unapproved review for moderation preview
                repository.insertReview(Review(productId = 1, userEmail = "troll@test.com", username = "SpamBot", rating = 1, comment = "Terrible, do not buy! Buy my crypto instead at scam.com!!!", isApproved = false))
            }
            isSeeding.value = false
        }
    }

    // AUTH ACTIONS
    fun loginWithCredentials(email: String, name: String, role: String = "user") {
        viewModelScope.launch {
            _authError.value = null
            val existing = repository.getUser(email)
            if (existing != null) {
                if (existing.isBlocked) {
                    _authError.value = "Your account has been temporarily blocked by administration."
                    return@launch
                }
                _loggedInUser.value = existing
            } else {
                val newUser = User(email = email, name = name, role = role, isBlocked = false)
                repository.insertUser(newUser)
                _loggedInUser.value = newUser
            }
        }
    }

    fun logout() {
        _loggedInUser.value = null
        _authError.value = null
        appliedCoupon.value = null
    }

    // CART ACTIONS
    fun addToCart(productId: Int, quantity: Int = 1) {
        val email = _loggedInUser.value?.email ?: return
        viewModelScope.launch {
            val currentCart = cartItems.value
            val existing = currentCart.find { it.productId == productId }
            if (existing != null) {
                repository.insertCartItem(existing.copy(quantity = existing.quantity + quantity))
            } else {
                repository.insertCartItem(CartItem(userEmail = email, productId = productId, quantity = quantity))
            }
        }
    }

    fun updateCartItemQuantity(cartItemId: Int, newQuantity: Int) {
        viewModelScope.launch {
            val currentCart = cartItems.value
            val item = currentCart.find { it.id == cartItemId } ?: return@launch
            if (newQuantity <= 0) {
                repository.deleteCartItem(item)
            } else {
                repository.insertCartItem(item.copy(quantity = newQuantity))
            }
        }
    }

    fun removeFromCart(cartItemId: Int) {
        viewModelScope.launch {
            val currentCart = cartItems.value
            val item = currentCart.find { it.id == cartItemId } ?: return@launch
            repository.deleteCartItem(item)
        }
    }

    // WISHLIST ACTIONS
    fun toggleWishlist(productId: Int) {
        val email = _loggedInUser.value?.email ?: return
        viewModelScope.launch {
            val isCurrent = wishlistProductIds.value.contains(productId)
            if (isCurrent) {
                repository.deleteWishlistItem(email, productId)
            } else {
                repository.insertWishlistItem(WishlistItem(userEmail = email, productId = productId))
            }
        }
    }

    // CHECKOUT ACTIONS
    fun applyCoupon(code: String) {
        viewModelScope.launch {
            couponMessage.value = null
            val coupon = repository.getCoupon(code.uppercase())
            if (coupon != null && coupon.isActive) {
                appliedCoupon.value = coupon
                couponMessage.value = "Successfully applied coupon: ${coupon.code} (-${coupon.discountPercent}%)"
            } else {
                appliedCoupon.value = null
                couponMessage.value = "Invalid or inactive coupon code."
            }
        }
    }

    fun clearCoupon() {
        appliedCoupon.value = null
        couponMessage.value = null
    }

    fun placeOrder(onOrderCompleted: (Int) -> Unit = {}) {
        val user = _loggedInUser.value ?: return
        val items = enrichedCart.value
        val sub = cartSubtotal.value
        if (items.isEmpty()) return

        val dPercent = appliedCoupon.value?.discountPercent ?: 0
        val finalAmount = sub * (1.0 - (dPercent / 100.0))

        viewModelScope.launch {
            // Build the string representation: ID:quantity;ID:quantity
            val itemsStr = items.joinToString(";") { "${it.product.id}:${it.cartItem.quantity}" }
            
            val newOrder = Order(
                userEmail = user.email,
                orderItemsStr = itemsStr,
                address = shippingAddress.value,
                paymentMethod = paymentMethod.value,
                paymentStatus = if (paymentMethod.value == "UPI") "Completed" else "Pending",
                orderStatus = "Pending",
                totalAmount = finalAmount
            )

            // Subtract quantities from products
            items.forEach { enriched ->
                val remainingStock = (enriched.product.stock - enriched.cartItem.quantity).coerceAtLeast(0)
                repository.insertProduct(enriched.product.copy(stock = remainingStock))
            }

            val newOrderId = repository.insertOrder(newOrder)
            // Clear cart
            repository.clearCartForUser(user.email)
            // Clean state
            shippingAddress.value = ""
            appliedCoupon.value = null
            couponMessage.value = null
            couponInput.value = ""
            checkoutStep.value = 1
            
            onOrderCompleted(newOrderId.toInt())
        }
    }

    // USER SIDE REVIEW ADDITION
    fun addReview(productId: Int, rating: Int, comment: String) {
        val user = _loggedInUser.value ?: return
        viewModelScope.launch {
            val review = Review(
                productId = productId,
                userEmail = user.email,
                username = user.name,
                rating = rating,
                comment = comment,
                isApproved = false // Review needs Admin approval first (Review moderation feature!)
            )
            repository.insertReview(review)
        }
    }

    // ADMIN ACTIONS
    fun adminDeleteProduct(id: Int) {
        viewModelScope.launch {
            repository.deleteProductById(id)
        }
    }

    fun adminSaveProduct(id: Int, name: String, desc: String, price: Double, category: String, rating: Double, stock: Int) {
        viewModelScope.launch {
            val product = Product(
                id = if (id == 0) 0 else id,
                name = name,
                description = desc,
                price = price,
                category = category,
                rating = rating,
                stock = stock
            )
            repository.insertProduct(product)
        }
    }

    fun adminCreateCategory(name: String) {
        viewModelScope.launch {
            if (name.isNotEmpty()) {
                repository.insertCategory(Category(name = name))
            }
        }
    }

    fun adminDeleteCategory(name: String) {
        viewModelScope.launch {
            repository.deleteCategory(Category(name = name))
        }
    }

    fun adminToggleUserBlocked(email: String, currentBlocked: Boolean) {
        viewModelScope.launch {
            val existing = repository.getUser(email)
            if (existing != null) {
                val updated = existing.copy(isBlocked = !currentBlocked)
                repository.updateUser(updated)
                // If we edited the currently logged-in user, kick them out
                if (loggedInUser.value?.email == email && updated.isBlocked) {
                    logout()
                }
            }
        }
    }

    fun adminUpdateOrderStatus(orderId: Int, newStatus: String) {
        viewModelScope.launch {
            val orders = adminAllOrders.value
            val o = orders.find { it.id == orderId } ?: return@launch
            val updated = o.copy(orderStatus = newStatus)
            repository.updateOrder(updated)
        }
    }

    fun adminApproveReview(reviewId: Int) {
        viewModelScope.launch {
            val revs = adminAllReviews.value
            val r = revs.find { it.id == reviewId } ?: return@launch
            val updated = r.copy(isApproved = true)
            repository.updateReview(updated)
        }
    }

    fun adminDisapproveOrDeleteReview(reviewId: Int) {
        viewModelScope.launch {
            val revs = adminAllReviews.value
            val r = revs.find { it.id == reviewId } ?: return@launch
            repository.deleteReview(r)
        }
    }

    fun adminSaveCoupon(code: String, percent: Int) {
        viewModelScope.launch {
            if (code.isNotEmpty() && percent in 1..99) {
                repository.insertCoupon(Coupon(code = code.uppercase(), discountPercent = percent, isActive = true))
            }
        }
    }

    fun adminDeleteCoupon(code: String) {
        viewModelScope.launch {
            repository.deleteCoupon(code)
        }
    }

    fun getReviewsForProduct(productId: Int): Flow<List<Review>> {
        return repository.getApprovedReviewsForProduct(productId)
    }
}

data class EnrichedCartItem(
    val cartItem: CartItem,
    val product: Product
)

class CrodytoViewModelFactory(private val repository: CrodytoRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(CrodytoViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return CrodytoViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
