package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.spring
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.*
import com.example.ui.*
import com.example.ui.theme.*

enum class Screen {
    LOGIN, SHOPPING, PRODUCT_DETAIL, CHECKOUT, ADMIN
}

enum class ShopTab {
    HOME, SHOP, CART, PROFILE
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val context = LocalContext.current
            val repository = remember {
                val db = AppDatabase.getDatabase(context)
                CrodytoRepository(
                    db.userDao(),
                    db.productDao(),
                    db.categoryDao(),
                    db.cartDao(),
                    db.wishlistDao(),
                    db.orderDao(),
                    db.reviewDao(),
                    db.couponDao()
                )
            }
            val factory = remember { CrodytoViewModelFactory(repository) }
            val viewModel: CrodytoViewModel = viewModel(factory = factory)

            var isDarkTheme by remember { mutableStateOf(false) }

            MyApplicationTheme(darkTheme = isDarkTheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    CrodytoAppShell(
                        viewModel = viewModel,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = { isDarkTheme = !isDarkTheme }
                    )
                }
            }
        }
    }
}

@Composable
fun CrodytoAppShell(
    viewModel: CrodytoViewModel,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val isSeeding by viewModel.isSeeding.collectAsStateWithLifecycle()

    val navigationStack = remember { mutableStateListOf(Screen.LOGIN) }
    var currentShopTab by remember { mutableStateOf(ShopTab.HOME) }
    var selectedProductId by remember { mutableStateOf<Int?>(null) }
    var lastCreatedOrderId by remember { mutableStateOf<Int?>(null) }

    val currentScreen = navigationStack.lastOrNull() ?: Screen.LOGIN

    // Handle system back press
    BackHandler(enabled = navigationStack.size > 1) {
        navigationStack.removeAt(navigationStack.lastIndex)
    }

    fun navigateTo(screen: Screen) {
        navigationStack.add(screen)
    }

    fun navigateBack() {
        if (navigationStack.size > 1) {
            navigationStack.removeAt(navigationStack.lastIndex)
        }
    }

    // Auto navigate on login state change
    LaunchedEffect(loggedInUser) {
        if (loggedInUser == null) {
            navigationStack.clear()
            navigationStack.add(Screen.LOGIN)
        } else if (navigationStack.size == 1 && navigationStack[0] == Screen.LOGIN) {
            navigationStack.clear()
            navigationStack.add(Screen.SHOPPING)
            currentShopTab = ShopTab.HOME
        }
    }

    if (isSeeding) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Initializing CRODYTO Premium...", fontWeight = FontWeight.Bold)
            }
        }
    } else {
        Scaffold(
            topBar = {
                if (currentScreen != Screen.LOGIN) {
                    CrodytoHeader(
                        viewModel = viewModel,
                        currentScreen = currentScreen,
                        isDarkTheme = isDarkTheme,
                        onToggleTheme = onToggleTheme,
                        onNavigateBack = { navigateBack() },
                        onAdminPanelClick = { navigateTo(Screen.ADMIN) }
                    )
                }
            },
            bottomBar = {
                if (currentScreen == Screen.SHOPPING) {
                    CrodytoBottomBar(
                        viewModel = viewModel,
                        activeTab = currentShopTab,
                        onTabSelected = { currentShopTab = it }
                    )
                }
            },
            contentWindowInsets = WindowInsets.safeDrawing
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding)) {
                AnimatedContent(
                    targetState = currentScreen,
                    transitionSpec = {
                        slideInHorizontally { it } + fadeIn() togetherWith
                                slideOutHorizontally { -it } + fadeOut()
                    },
                    label = "ScreenTransition"
                ) { screen ->
                    when (screen) {
                        Screen.LOGIN -> LoginScreen(viewModel)
                        Screen.SHOPPING -> {
                            ShoppingTabRouter(
                                viewModel = viewModel,
                                activeTab = currentShopTab,
                                onProductClick = { pid ->
                                    selectedProductId = pid
                                    navigateTo(Screen.PRODUCT_DETAIL)
                                },
                                onProceedToCheckout = {
                                    navigateTo(Screen.CHECKOUT)
                                },
                                onCategorySelected = { cat ->
                                    viewModel.selectedCategory.value = cat
                                    currentShopTab = ShopTab.SHOP
                                }
                            )
                        }
                        Screen.PRODUCT_DETAIL -> {
                            selectedProductId?.let { pid ->
                                ProductDetailScreen(
                                    viewModel = viewModel,
                                    productId = pid,
                                    onBackClick = { navigateBack() }
                                )
                            }
                        }
                        Screen.CHECKOUT -> {
                            CheckoutScreen(
                                viewModel = viewModel,
                                onOrderCreated = { oid ->
                                    lastCreatedOrderId = oid
                                },
                                onFinish = {
                                    navigationStack.clear()
                                    navigationStack.addAll(listOf(Screen.SHOPPING))
                                    currentShopTab = ShopTab.PROFILE
                                }
                            )
                        }
                        Screen.ADMIN -> {
                            AdminDashboardScreen(
                                viewModel = viewModel,
                                onBackClick = { navigateBack() }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENT: HEADER
// ==========================================
@Composable
fun CrodytoHeader(
    viewModel: CrodytoViewModel,
    currentScreen: Screen,
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit,
    onNavigateBack: () -> Unit,
    onAdminPanelClick: () -> Unit
) {
    val loggedInUser by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val enrichedCart by viewModel.enrichedCart.collectAsStateWithLifecycle()
    val cartCount = enrichedCart.sumOf { it.cartItem.quantity }

    Surface(
        tonalElevation = 8.dp,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (currentScreen != Screen.SHOPPING) {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                } else {
                    Icon(
                        Icons.Default.ShoppingBag,
                        contentDescription = "Logo",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = "CRODYTO",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 1.5.sp,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Theme Toggle
                IconButton(onClick = onToggleTheme) {
                    Icon(
                        imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                        contentDescription = "Theme Toggle",
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }

                // Header Cart Indicator
                Box(contentAlignment = Alignment.TopEnd) {
                    Icon(
                        Icons.Default.ShoppingCart,
                        contentDescription = "Cart",
                        modifier = Modifier.padding(6.dp)
                    )
                    if (cartCount > 0) {
                        Box(
                            modifier = Modifier
                                .size(18.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.secondary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = cartCount.toString(),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // If user is Admin, show an direct Administrator Dashboard button
                if (loggedInUser?.role == "admin" && currentScreen != Screen.ADMIN) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = onAdminPanelClick,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        modifier = Modifier.height(34.dp).testTag("header_admin_button")
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = "Admin", modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Admin", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// COMPONENT: BOTTOM BAR
// ==========================================
@Composable
fun CrodytoBottomBar(
    viewModel: CrodytoViewModel,
    activeTab: ShopTab,
    onTabSelected: (ShopTab) -> Unit
) {
    val enrichedCart by viewModel.enrichedCart.collectAsStateWithLifecycle()
    val cartCount = enrichedCart.sumOf { it.cartItem.quantity }

    NavigationBar(
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp
    ) {
        val tabs = listOf(
            Triple(ShopTab.HOME, "Home", Icons.Default.Home),
            Triple(ShopTab.SHOP, "Shop", Icons.Default.Search),
            Triple(ShopTab.CART, "Cart", Icons.Default.ShoppingCart),
            Triple(ShopTab.PROFILE, "Profile", Icons.Default.Person)
        )
        tabs.forEach { (tab, label, icon) ->
            NavigationBarItem(
                selected = activeTab == tab,
                onClick = { onTabSelected(tab) },
                icon = {
                    if (tab == ShopTab.CART && cartCount > 0) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(icon, contentDescription = label)
                            Box(
                                modifier = Modifier
                                    .size(14.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondary)
                                    .offset(x = 6.dp, y = (-4).dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = cartCount.toString(),
                                    color = Color.White,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    } else {
                        Icon(icon, contentDescription = label)
                    }
                },
                label = { Text(label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.primary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }
    }
}

// ==========================================
// ROUTER: SHOPPING SUB-TABS
// ==========================================
@Composable
fun ShoppingTabRouter(
    viewModel: CrodytoViewModel,
    activeTab: ShopTab,
    onProductClick: (Int) -> Unit,
    onProceedToCheckout: () -> Unit,
    onCategorySelected: (String) -> Unit
) {
    when (activeTab) {
        ShopTab.HOME -> HomeScreen(viewModel, onProductClick, onCategorySelected)
        ShopTab.SHOP -> ShopScreen(viewModel, onProductClick)
        ShopTab.CART -> CartScreen(viewModel, onProceedToCheckout, onSwapToShop = { })
        ShopTab.PROFILE -> ProfileScreen(viewModel)
    }
}

// ==========================================
// SCREEN 1: LOGIN SCREEN
// ==========================================
@Composable
fun LoginScreen(viewModel: CrodytoViewModel) {
    val authError by viewModel.authError.collectAsStateWithLifecycle()
    var email by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var showGoogleLoader by remember { mutableStateOf(false) }

    if (showGoogleLoader) {
        Dialog(onDismissRequest = {}) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = "Authenticating with Google SSO...",
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Connecting simulation node...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        LaunchedEffect(Unit) {
            kotlinx.coroutines.delay(1200)
            viewModel.loginWithCredentials("crodyto@gmail.com", "Jane Watson")
            showGoogleLoader = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Elegant Luxury Icon and Tagline
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.ShoppingBag,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(
                "CRODYTO",
                fontSize = 32.sp,
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = 2.sp,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                "Exclusive Couture & Modern Utility",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Auth Error Handler View
            authError?.let {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "Digital Credential Node",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it },
                        label = { Text("E-mail Address") },
                        modifier = Modifier.fillMaxWidth().testTag("username_input"),
                        leadingIcon = { Icon(Icons.Default.Email, contentDescription = null) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Customer Name") },
                        modifier = Modifier.fillMaxWidth(),
                        leadingIcon = { Icon(Icons.Default.Person, contentDescription = null) },
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    Button(
                        onClick = {
                            if (email.contains("@")) {
                                viewModel.loginWithCredentials(email, name.ifEmpty { "Customer" })
                            } else {
                                viewModel.loginWithCredentials("buyer@test.com", "Alex Rivera")
                            }
                        },
                        modifier = Modifier.fillMaxWidth().testTag("submit_button"),
                        enabled = email.isNotEmpty()
                    ) {
                        Text("Connect Session Securely")
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Simulated Google Login
            OutlinedButton(
                onClick = { showGoogleLoader = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(30.dp),
                border = BorderStroke(1.2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
            ) {
                Icon(
                    Icons.Default.GroupWork,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Federated Sign-In with Google")
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Rapid Admin & User Login buttons
            Text("Simulation Quick Access Profile", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedCard(
                    onClick = { viewModel.loginWithCredentials("user@crodyto.com", "Jane Doe") },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.ShoppingBag, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Guest User", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }

                OutlinedCard(
                    onClick = { viewModel.loginWithCredentials("admin@crodyto.com", "Director Admin", "admin") },
                    modifier = Modifier.weight(1f)
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Administrator", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 2: HOME COMPOSABLE
// ==========================================
@Composable
fun HomeScreen(
    viewModel: CrodytoViewModel,
    onProductClick: (Int) -> Unit,
    onCategorySelected: (String) -> Unit
) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Welcome Header & Clean Minimalist Hero Banner
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 20.dp)
            ) {
                Text(
                    "CRODYTO",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary,
                    letterSpacing = 1.sp
                )
                Text(
                    "Curated essentials with beautiful, refined aesthetics.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Clean Minimalism Main Hero Promo Card
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 20.dp)
                    .height(130.dp),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Box(modifier = Modifier.fillMaxSize()) {
                    // Accent minimalist circles
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .offset(x = 20.dp, y = 20.dp)
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.1f))
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .offset(x = 50.dp, y = (-50).dp)
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.05f))
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(20.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "LIMITED OFFER",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.82f),
                            letterSpacing = 2.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Upgrade Your Lifestyle",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            "Up to 40% Off Tech",
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.9f)
                        )
                    }
                }
            }
        }

        // Banners Carousel Row (For keeping discount functionality active)
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                val promotionalBanners = listOf(
                    Triple("WELCOME10", "WELCOME 10% DISCOUNT", Brush.horizontalGradient(listOf(Color(0xFF1E3C72), Color(0xFF2A5298)))),
                    Triple("CRODYTO30", "SUPER SAVING: 30% OFF\nCode: CRODYTO30", Brush.horizontalGradient(listOf(Color(0xFFE65C00), Color(0xFFF9D423)))),
                    Triple("SUPER50", "HALF PRICE HARMONY!\nGet 50% Off select headphones", Brush.horizontalGradient(listOf(Color(0xFF11998e), Color(0xFF38ef7d))))
                )
                items(promotionalBanners) { (code, title, gradient) ->
                    Card(
                        modifier = Modifier
                            .width(280.dp)
                            .height(110.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(gradient)
                                .padding(16.dp)
                        ) {
                            Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
                                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Copon Code: $code", color = Color.White.copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold)
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(Color.White.copy(alpha = 0.25f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)
                                    ) {
                                        Text("COPY", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Category Fast Filters list
        item {
            Text(
                "Core Catalog Collections",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 20.dp)
            ) {
                item {
                    OutlinedCard(
                        onClick = { onCategorySelected("") },
                        shape = RoundedCornerShape(24.dp),
                        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Text("All Collections", modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.Bold)
                    }
                }
                items(categories) { cat ->
                    OutlinedCard(
                        onClick = { onCategorySelected(cat.name) },
                        shape = RoundedCornerShape(24.dp)
                    ) {
                        Text(cat.name, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }

        // Featured Products Segment
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Featured Luxuries",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "Recent Arrivals",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Double items row structure to keep layout incredibly high performance
        val featuredList = products.take(6)
        if (featuredList.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp), contentAlignment = Alignment.Center
                ) {
                    Text("Refreshing catalog node details...", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            items(featuredList.chunked(2)) { pair ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    pair.forEach { product ->
                        Box(modifier = Modifier.weight(1f)) {
                            ProductCard(
                                product = product,
                                onClick = { onProductClick(product.id) },
                                onWishlistToggle = { viewModel.toggleWishlist(product.id) },
                                onAddToCart = { viewModel.addToCart(product.id, 1) },
                                isWishlisted = viewModel.wishlistProductIds.collectAsStateWithLifecycle().value.contains(product.id)
                            )
                        }
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }

        // Space Buffer
        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

// ==========================================
// THE PRODUCT CARD COMPONENT
// ==========================================
@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    onWishlistToggle: () -> Unit,
    onAddToCart: () -> Unit,
    isWishlisted: Boolean
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            // Elegant Visual Box instead of heavy loading image URLs
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                            )
                        )
                    )
            ) {
                // Category Tag
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.primary)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(product.category, color = Color.White, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                }

                // Wishlist Icon Button top right
                IconButton(
                    onClick = onWishlistToggle,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                ) {
                    Icon(
                        imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Wishlist",
                        tint = if (isWishlisted) Color.Red else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Decorative Bag Silhouette
                Icon(
                    Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                    modifier = Modifier
                        .size(60.dp)
                        .align(Alignment.Center)
                )
            }

            // Descriptive Information Block
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = product.name,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(2.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Star, contentDescription = null, tint = GoldenSun, modifier = Modifier.size(13.dp))
                    Text(
                        text = " ${product.rating}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "$${String.format("%.2f", product.price)}",
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 16.sp,
                        color = MaterialTheme.colorScheme.primary
                    )

                    IconButton(
                        onClick = onAddToCart,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Icon(
                            Icons.Default.Add,
                            contentDescription = "Add to Cart",
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 3: CATALOG SHOP SCREEN WITH FILTERS
// ==========================================
@Composable
fun ShopScreen(viewModel: CrodytoViewModel, onProductClick: (Int) -> Unit) {
    val search by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedCategory by viewModel.selectedCategory.collectAsStateWithLifecycle()
    val maxPrice by viewModel.maxPrice.collectAsStateWithLifecycle()
    val sortOrder by viewModel.sortOrder.collectAsStateWithLifecycle()
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val wishlistProductIds by viewModel.wishlistProductIds.collectAsStateWithLifecycle()

    var showFiltersDeck by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(0.dp)
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                // Search row
                OutlinedTextField(
                    value = search,
                    onValueChange = { viewModel.searchQuery.value = it },
                    placeholder = { Text("Search premium essentials...") },
                    modifier = Modifier.fillMaxWidth(),
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        IconButton(onClick = { showFiltersDeck = !showFiltersDeck }) {
                            Icon(
                                if (showFiltersDeck) Icons.Default.Close else Icons.Default.Tune,
                                contentDescription = "Toggle Filters"
                            )
                        }
                    },
                    singleLine = true
                )

                // Filters panel sliding drawer animation
                AnimatedVisibility(visible = showFiltersDeck) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Text("Interactive Control Deck", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(4.dp))

                        // Category quick drop selector
                        Text("Category filter:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val cats = listOf("", "Electronics", "Fashion", "Footwear", "Accessories", "Home & Living")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(cats) { c ->
                                FilterChip(
                                    selected = selectedCategory == c,
                                    onClick = { viewModel.selectedCategory.value = c },
                                    label = { Text(if (c.isEmpty()) "All" else c) }
                                )
                            }
                        }

                        // Max Price Slider
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Max Limit Expense:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text("$${maxPrice.toInt()}", fontWeight = FontWeight.ExtraBold)
                        }
                        Slider(
                            value = maxPrice,
                            onValueChange = { viewModel.maxPrice.value = it },
                            valueRange = 0f..500f
                        )

                        // Sorting Dropdown row
                        Text("Arrange Catalog:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        val sorts = listOf("Default", "Price Low-High", "Price High-Low", "Rating")
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(sorts) { s ->
                                FilterChip(
                                    selected = sortOrder == s,
                                    onClick = { viewModel.sortOrder.value = s },
                                    label = { Text(s) }
                                )
                            }
                        }
                    }
                }
            }
        }

        // Double columns product grid list
        if (products.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No premium matches. Reset search controls.", fontWeight = FontWeight.SemiBold)
                }
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(products) { item ->
                    ProductCard(
                        product = item,
                        onClick = { onProductClick(item.id) },
                        onWishlistToggle = { viewModel.toggleWishlist(item.id) },
                        onAddToCart = { viewModel.addToCart(item.id, 1) },
                        isWishlisted = wishlistProductIds.contains(item.id)
                    )
                }
            }
        }
    }
}

// ==========================================
// SCREEN 4: DETAILED PRODUCT COMPOSABLE
// ==========================================
@Composable
fun ProductDetailScreen(
    viewModel: CrodytoViewModel,
    productId: Int,
    onBackClick: () -> Unit
) {
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val product = products.find { it.id == productId }

    val wishlistProductIds by viewModel.wishlistProductIds.collectAsStateWithLifecycle()
    val isWishlisted = wishlistProductIds.contains(productId)

    // User ratings writing elements
    var userStars by remember { mutableStateOf(5) }
    var reviewComment by remember { mutableStateOf("") }
    var reviewQueueMessage by remember { mutableStateOf(false) }

    val dbReviews by viewModel.getReviewsForProduct(productId).collectAsStateWithLifecycle(initialValue = emptyList())

    if (product == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        // Splendor display picture block
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(240.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                MaterialTheme.colorScheme.primaryContainer,
                                MaterialTheme.colorScheme.surface
                            )
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Status Badge Left
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(16.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (product.stock > 0) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        if (product.stock > 0) "In Stock: ${product.stock} left" else "Out of stock",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Icon(
                    Icons.Default.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                    modifier = Modifier.size(100.dp)
                )
            }
        }

        // Textual facts details block
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        product.category.uppercase(),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.secondary,
                        fontSize = 11.sp
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = GoldenSun, modifier = Modifier.size(16.dp))
                        Text(" ${product.rating}", fontWeight = FontWeight.ExtraBold)
                    }
                }

                Text(
                    product.name,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                )

                Text(
                    "$${String.format("%.2f", product.price)}",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Black,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Text("Description", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(
                    product.description,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(vertical = 4.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Actions block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = { viewModel.addToCart(product.id, 1) },
                        modifier = Modifier.weight(1f),
                        enabled = product.stock > 0
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Order to Cart")
                    }

                    OutlinedButton(
                        onClick = { viewModel.toggleWishlist(product.id) }
                    ) {
                        Icon(
                            imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = null,
                            tint = if (isWishlisted) Color.Red else MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Interactive Reviews moderation card list
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                Divider()
                Spacer(modifier = Modifier.height(12.dp))
                Text("Verified Customer Reviews", fontSize = 16.sp, fontWeight = FontWeight.Bold)

                if (dbReviews.isEmpty()) {
                    Text(
                        "No approved reviews written yet. Be the first to review!",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 12.dp)
                    )
                } else {
                    dbReviews.forEach { review ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(review.username, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Row {
                                        repeat(5) { star ->
                                            Icon(
                                                Icons.Default.Star,
                                                contentDescription = null,
                                                tint = if (star < review.rating) GoldenSun else Color.LightGray,
                                                modifier = Modifier.size(10.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(review.comment, fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }

        // Add Review Input area with Admin verification caution notices
        item {
            Card(
                modifier = Modifier.padding(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Express store review feedback", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Rating selection:", fontSize = 12.sp)
                        repeat(5) { ind ->
                            IconButton(
                                onClick = { userStars = ind + 1 },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = null,
                                    tint = if (ind < userStars) GoldenSun else Color.LightGray,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    OutlinedTextField(
                        value = reviewComment,
                        onValueChange = { reviewComment = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(80.dp),
                        placeholder = { Text("Write honest feedback review...") }
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            if (reviewComment.isNotEmpty()) {
                                viewModel.addReview(product.id, userStars, reviewComment)
                                reviewComment = ""
                                reviewQueueMessage = true
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Submit to Moderation Queue")
                    }

                    if (reviewQueueMessage) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(8.dp)
                        ) {
                            Text(
                                "Your review has been successfully queued! Wait for Admin approval to be visible.",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 5: CART COMPOSABLE
// ==========================================
@Composable
fun CartScreen(
    viewModel: CrodytoViewModel,
    onProceedToCheckout: () -> Unit,
    onSwapToShop: () -> Unit
) {
    val enrichedCart by viewModel.enrichedCart.collectAsStateWithLifecycle()
    val subtotal by viewModel.cartSubtotal.collectAsStateWithLifecycle()
    val appliedCoupon by viewModel.appliedCoupon.collectAsStateWithLifecycle()
    val couponMessage by viewModel.couponMessage.collectAsStateWithLifecycle()
    val couponCodeInput by viewModel.couponInput.collectAsStateWithLifecycle()

    val total = if (appliedCoupon != null) {
        subtotal * (1.0 - (appliedCoupon!!.discountPercent / 100.0))
    } else {
        subtotal
    }

    if (enrichedCart.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(Icons.Default.RemoveShoppingCart, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(modifier = Modifier.height(12.dp))
                Text("Your premium cart is empty", fontWeight = FontWeight.SemiBold, fontSize = 16.sp)
                Text("Select gorgeous essentials from the catalog", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(enrichedCart) { item ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Small Gradient Icon Box
                        Box(
                            modifier = Modifier
                                .size(60.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Brush.linearGradient(listOf(Color(0xFFE2E8F0), Color(0xFFCBD5E1)))),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(item.product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("$${item.product.price}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, fontSize = 12.sp)

                            Spacer(modifier = Modifier.height(4.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                // Qty modifier
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { viewModel.updateCartItemQuantity(item.cartItem.id, item.cartItem.quantity - 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Remove, contentDescription = "Decrement", modifier = Modifier.size(16.dp))
                                    }
                                    Text(" ${item.cartItem.quantity} ", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    IconButton(
                                        onClick = { viewModel.updateCartItemQuantity(item.cartItem.id, item.cartItem.quantity + 1) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = "Increment", modifier = Modifier.size(16.dp))
                                    }
                                }

                                IconButton(
                                    onClick = { viewModel.removeFromCart(item.cartItem.id) },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Coupons and Calculations check deck
        Surface(
            tonalElevation = 6.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                // Apply Coupon input row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = couponCodeInput,
                        onValueChange = { viewModel.couponInput.value = it },
                        modifier = Modifier.weight(1f),
                        placeholder = { Text("Coupon code (e.g. CRODYTO30)") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { viewModel.applyCoupon(couponCodeInput) }
                    ) {
                        Text("Apply")
                    }
                }

                couponMessage?.let {
                    Text(
                        text = it,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (appliedCoupon != null) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Price Subtotal:")
                    Text("$${String.format("%.2f", subtotal)}")
                }

                if (appliedCoupon != null) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Applied Code discount (${appliedCoupon!!.discountPercent}%):", color = Color(0xFF2E7D32))
                        Text("-$${String.format("%.2f", subtotal * (appliedCoupon!!.discountPercent / 100.0))}", color = Color(0xFF2E7D32))
                    }
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Price Due:", fontWeight = FontWeight.ExtraBold)
                    Text("$${String.format("%.2f", total)}", fontWeight = FontWeight.Black, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
                }

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onProceedToCheckout,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Checkout Order Securely")
                }
            }
        }
    }
}

// ==========================================
// SCREEN 6: PROFILE PORTAL COMPOSABLE
// ==========================================
@Composable
fun ProfileScreen(viewModel: CrodytoViewModel) {
    val user by viewModel.loggedInUser.collectAsStateWithLifecycle()
    val orders by viewModel.userOrders.collectAsStateWithLifecycle()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp)
    ) {
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = (user?.name ?: "U").take(1).uppercase(),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))
                    Text(user?.name ?: "Customer", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                    Text(user?.email ?: "", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(14.dp))
                    Button(
                        onClick = { viewModel.logout() },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(Icons.Default.ExitToApp, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Log Out Profile Session")
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
            Text("Historical Customer Orders", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(8.dp))
        }

        if (orders.isEmpty()) {
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text("No orders placed yet. Add gorgeous products to checkout!")
                    }
                }
            }
        } else {
            items(orders) { o ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 6.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Order ID: #${o.id}", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("$${String.format("%.2f", o.totalAmount)}", fontWeight = FontWeight.Black)
                        }

                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Delivery Target Address: ${o.address}", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("Method: ${o.paymentMethod} • Status: ${o.paymentStatus}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)

                        Spacer(modifier = Modifier.height(12.dp))

                        // Graphical tracker bar of statuses: Pending -> Dispatched -> Out for Delivery -> Delivered
                        val steps = listOf("Pending", "Dispatched", "Out for Delivery", "Delivered")
                        val currentStepIndex = steps.indexOf(o.orderStatus).coerceAtLeast(0)

                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("Tracker status: ", fontSize = 11.sp)
                                Text(o.orderStatus.uppercase(), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                steps.forEachIndexed { index, _ ->
                                    val active = index <= currentStepIndex
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .height(5.dp)
                                            .background(if (active) MaterialTheme.colorScheme.primary else Color.LightGray)
                                    )
                                    if (index < steps.size - 1) {
                                        Spacer(modifier = Modifier.width(3.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 7: CHECKOUT COMPOSABLE
// ==========================================
@Composable
fun CheckoutScreen(
    viewModel: CrodytoViewModel,
    onOrderCreated: (Int) -> Unit,
    onFinish: () -> Unit
) {
    val step by viewModel.checkoutStep.collectAsStateWithLifecycle()
    val subtotal by viewModel.cartSubtotal.collectAsStateWithLifecycle()
    val address by viewModel.shippingAddress.collectAsStateWithLifecycle()
    val paymentM by viewModel.paymentMethod.collectAsStateWithLifecycle()
    val coupon by viewModel.appliedCoupon.collectAsStateWithLifecycle()

    val total = if (coupon != null) {
        subtotal * (1.0 - (coupon!!.discountPercent / 100.0))
    } else {
        subtotal
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Step Indicators Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val stepNames = listOf("Delivery", "Payment", "Order Confirmed")
            stepNames.forEachIndexed { ix, name ->
                val active = step >= ix + 1
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape)
                            .background(if (active) MaterialTheme.colorScheme.primary else Color.LightGray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text((ix + 1).toString(), color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    }
                    Text(name, fontSize = 11.sp, fontWeight = if (active) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when (step) {
            1 -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Operational Shipping Address", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("Fill in the premium destination node location details securely.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(12.dp))

                        OutlinedTextField(
                            value = address,
                            onValueChange = { viewModel.shippingAddress.value = it },
                            label = { Text("Complete Delivery Target Address") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("Flat No, Bld Name, City, PIN code, State...") }
                        )

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = { viewModel.checkoutStep.value = 2 },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = address.isNotEmpty()
                        ) {
                            Text("Proceed to Payments")
                        }
                    }
                }
            }
            2 -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text("Simulated Payment Terminal", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(modifier = Modifier.height(8.dp))

                        // Choices
                        OutlinedCard(
                            onClick = { viewModel.paymentMethod.value = "UPI" },
                            modifier = Modifier.padding(vertical = 4.dp),
                            border = BorderStroke(1.2.dp, if (paymentM == "UPI") MaterialTheme.colorScheme.primary else Color.LightGray)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = paymentM == "UPI", onClick = { viewModel.paymentMethod.value = "UPI" })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Simulated UPI App Transfer", fontWeight = FontWeight.Bold)
                                    Text("Simulated payment routing transfer instantly.", fontSize = 11.sp)
                                }
                            }
                        }

                        OutlinedCard(
                            onClick = { viewModel.paymentMethod.value = "COD" },
                            modifier = Modifier.padding(vertical = 4.dp),
                            border = BorderStroke(1.2.dp, if (paymentM == "COD") MaterialTheme.colorScheme.primary else Color.LightGray)
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(selected = paymentM == "COD", onClick = { viewModel.paymentMethod.value = "COD" })
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text("Cash On Delivery (COD)", fontWeight = FontWeight.Bold)
                                    Text("Pay standard currencies on cargo arrival.", fontSize = 11.sp)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text("Total Bill details:", fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Payable Aggregate Amount:")
                            Text("$${String.format("%.2f", total)}", fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                        }

                        Spacer(modifier = Modifier.height(20.dp))

                        Button(
                            onClick = {
                                viewModel.placeOrder(onOrderCompleted = { id ->
                                    onOrderCreated(id)
                                    viewModel.checkoutStep.value = 3
                                })
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Process & Formulate Order")
                        }
                    }
                }
            }
            3 -> {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(64.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text("Order Completed Successfully!", fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
                        Text("The cargo node packaging has formulated your request.", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                        Spacer(modifier = Modifier.height(16.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer)
                                .padding(12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("ESTIMATED ARRIVAL IN CARGO", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("2-3 Business Days", fontWeight = FontWeight.Black, fontSize = 16.sp, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onFinish,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Close & Track Orders")
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// SCREEN 8: ADMIN MASTER COMPOSABLE
// ==========================================
@Composable
fun AdminDashboardScreen(
    viewModel: CrodytoViewModel,
    onBackClick: () -> Unit
) {
    val orders by viewModel.adminAllOrders.collectAsStateWithLifecycle()
    val users by viewModel.usersList.collectAsStateWithLifecycle()
    val products by viewModel.filteredProducts.collectAsStateWithLifecycle()
    val reviews by viewModel.adminAllReviews.collectAsStateWithLifecycle()
    val coupons by viewModel.coupons.collectAsStateWithLifecycle()

    var activeAdminTab by remember { mutableStateOf("DASHBOARD") } // "DASHBOARD", "PRODUCTS", "CATEGORIES", "ORDERS", "REVIEWS", "USERS", "COUPONS"

    Column(modifier = Modifier.fillMaxSize()) {
        // Administration Sidebar/Tab Selection
        ScrollableTabRow(
            selectedTabIndex = when (activeAdminTab) {
                "DASHBOARD" -> 0
                "PRODUCTS" -> 1
                "CATEGORIES" -> 2
                "ORDERS" -> 3
                "REVIEWS" -> 4
                "USERS" -> 5
                else -> 6
            }
        ) {
            val tabs = listOf("DASHBOARD", "PRODUCTS", "CATEGORIES", "ORDERS", "REVIEWS", "USERS", "COUPONS")
            tabs.forEach { active ->
                Tab(
                    selected = activeAdminTab == active,
                    onClick = { activeAdminTab = active },
                    text = { Text(active, fontSize = 11.sp, fontWeight = FontWeight.Bold) }
                )
            }
        }

        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            when (activeAdminTab) {
                "DASHBOARD" -> AdminAnalyticsDashboard(orders, users, products)
                "PRODUCTS" -> AdminProductCrud(viewModel, products)
                "CATEGORIES" -> AdminCategoryManager(viewModel)
                "ORDERS" -> AdminOrderTracker(viewModel, orders)
                "REVIEWS" -> AdminReviewModerationCabinet(viewModel, reviews)
                "USERS" -> AdminUserModerator(viewModel, users)
                "COUPONS" -> AdminCouponManager(viewModel, coupons)
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: ANALYTICS DASHBOARD
// ==========================================
@Composable
fun AdminAnalyticsDashboard(
    orders: List<Order>,
    users: List<User>,
    products: List<Product>
) {
    val totalRevenue = orders.sumOf { it.totalAmount }
    val lowStock = products.filter { it.stock < 5 }
    val normalUsersCount = users.filter { it.role == "user" }.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Store Performance Analytics", fontWeight = FontWeight.ExtraBold, fontSize = 18.sp, color = MaterialTheme.colorScheme.primary)
        }

        // Analytical Cards Grid Row
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Gross Revenue", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("$${String.format("%.2f", totalRevenue)}", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Dispatched Orders", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${orders.size} unit checkouts", fontWeight = FontWeight.Black, fontSize = 20.sp, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Registered Customers", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("$normalUsersCount users", fontWeight = FontWeight.Black, fontSize = 20.sp)
                    }
                }

                Card(modifier = Modifier.weight(1f)) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("Critical Stock alerts", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Text("${lowStock.size} alerts", fontWeight = FontWeight.Black, fontSize = 20.sp, color = if (lowStock.isNotEmpty()) Color.Red else Color.Green)
                    }
                }
            }
        }

        // Beautiful Graphical Chart drawn via Jetpack Compose Canvas in-place
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.2.dp, Color.LightGray.copy(alpha = 0.5f))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Category Formulation Sales Distribution Chart", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Group orders item counters by category
                    // Format of items: productid:quantity
                    // Parse categories directly
                    val mockChartPoints = listOf(
                        "Electronics" to 420f,
                        "Fashion" to 290f,
                        "Footwear" to 190f,
                        "Accessories" to 340f,
                        "Home & Living" to 110f
                    )

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 12.dp)
                    ) {
                        val maxSalesVal = 500f
                        val barSpacing = 16.dp.toPx()
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val itemsCount = mockChartPoints.size
                        val totalSpacing = barSpacing * (itemsCount - 1)
                        val barWidth = (canvasWidth - totalSpacing) / itemsCount

                        mockChartPoints.forEachIndexed { i, (name, sales) ->
                            val activeValNormalized = (sales / maxSalesVal) * canvasHeight
                            val xPos = i * (barWidth + barSpacing)
                            val yPos = canvasHeight - activeValNormalized

                            // Draw graphical bars
                            drawRect(
                                color = if (i % 2 == 0) SkyBlue else GoldenSun,
                                topLeft = Offset(xPos, yPos),
                                size = Size(barWidth, activeValNormalized)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Labels description legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        mockChartPoints.forEach { (name, _) ->
                            Text(
                                name.take(5),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(top = 4.dp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        // Alert block listing low inventory
        if (lowStock.isNotEmpty()) {
            item {
                Text("Low Inventory Warnings list", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = MaterialTheme.colorScheme.error)
            }
            items(lowStock) { prod ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(prod.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text("Category: ${prod.category}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.error)
                                .padding(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Text("${prod.stock} left", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: PRODUCTS MANAGER CRUD
// ==========================================
@Composable
fun AdminProductCrud(
    viewModel: CrodytoViewModel,
    products: List<Product>
) {
    var showProductDialogue by remember { mutableStateOf(false) }

    // Forms fields
    var editId by remember { mutableStateOf(0) }
    var pName by remember { mutableStateOf("") }
    var pDesc by remember { mutableStateOf("") }
    var pPrice by remember { mutableStateOf("") }
    var pCategory by remember { mutableStateOf("Electronics") }
    var pStock by remember { mutableStateOf("") }

    if (showProductDialogue) {
        Dialog(onDismissRequest = { showProductDialogue = false }) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        if (editId == 0) "Formulate New Product" else "Update Product Attributes",
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )

                    OutlinedTextField(value = pName, onValueChange = { pName = it }, label = { Text("Product Name") })
                    OutlinedTextField(value = pDesc, onValueChange = { pDesc = it }, label = { Text("Description") })
                    OutlinedTextField(value = pPrice, onValueChange = { pPrice = it }, label = { Text("Price ($)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    OutlinedTextField(value = pStock, onValueChange = { pStock = it }, label = { Text("In Stock Inventory") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                    // Simple category drop choice
                    val cats = listOf("Electronics", "Fashion", "Footwear", "Accessories", "Home & Living")
                    Column {
                        Text("Category Picker:", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                            cats.forEach { sc ->
                                FilterChip(
                                    selected = pCategory == sc,
                                    onClick = { pCategory = sc },
                                    label = { Text(sc) }
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(onClick = { showProductDialogue = false }, modifier = Modifier.weight(1f)) {
                            Text("Cancel")
                        }

                        Button(
                            onClick = {
                                if (pName.isNotEmpty()) {
                                    viewModel.adminSaveProduct(
                                        id = editId,
                                        name = pName,
                                        desc = pDesc,
                                        price = pPrice.toDoubleOrNull() ?: 19.99,
                                        category = pCategory,
                                        rating = 4.5,
                                        stock = pStock.toIntOrNull() ?: 10
                                    )
                                    showProductDialogue = false
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Node")
                        }
                    }
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Button(
            onClick = {
                editId = 0
                pName = ""
                pDesc = ""
                pPrice = ""
                pCategory = "Electronics"
                pStock = ""
                showProductDialogue = true
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(6.dp))
            Text("Create New Catalog Product")
        }

        Spacer(modifier = Modifier.height(10.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(products) { p ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(p.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text("Price: $${p.price} • Stock: ${p.stock}", style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(
                            onClick = {
                                editId = p.id
                                pName = p.name
                                pDesc = p.description
                                pPrice = p.price.toString()
                                pCategory = p.category
                                pStock = p.stock.toString()
                                showProductDialogue = true
                            }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", tint = MaterialTheme.colorScheme.primary)
                        }

                        IconButton(
                            onClick = { viewModel.adminDeleteProduct(p.id) }
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: CATEGORY CREATION MANAGER
// ==========================================
@Composable
fun AdminCategoryManager(viewModel: CrodytoViewModel) {
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    var inputCat by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputCat,
                onValueChange = { inputCat = it },
                modifier = Modifier.weight(1f),
                label = { Text("New Category Label") },
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = {
                    if (inputCat.isNotEmpty()) {
                        viewModel.adminCreateCategory(inputCat)
                        inputCat = ""
                    }
                }
            ) {
                Text("Insert")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(categories) { cat ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .padding(10.dp)
                            .fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(cat.name, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { viewModel.adminDeleteCategory(cat.name) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: ORDER TRACKER MANAGER
// ==========================================
@Composable
fun AdminOrderTracker(
    viewModel: CrodytoViewModel,
    orders: List<Order>
) {
    if (orders.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No dispatches registered.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(orders) { order ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Order Link: #${order.id}", fontWeight = FontWeight.ExtraBold)
                        Text("$${String.format("%.2f", order.totalAmount)}", fontWeight = FontWeight.Black)
                    }

                    Text("Buyer profile: ${order.userEmail}", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("Target criteria: ${order.address}", fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(10.dp))

                    // Change track dropdown row
                    Text("Modify Shipment destination tracker:", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val shipmentStepsList = listOf("Pending", "Dispatched", "Out for Delivery", "Delivered")
                        shipmentStepsList.forEach { valStep ->
                            FilterChip(
                                selected = order.orderStatus == valStep,
                                onClick = { viewModel.adminUpdateOrderStatus(order.id, valStep) },
                                label = { Text(valStep) }
                            )
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: REVIEWS MODERATION CABINET
// ==========================================
@Composable
fun AdminReviewModerationCabinet(
    viewModel: CrodytoViewModel,
    reviews: List<Review>
) {
    // Reviews with status unapproved / approved triggers
    if (reviews.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No reviews written for catalog.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(reviews) { r ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (r.isApproved) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                )
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(r.username, fontWeight = FontWeight.Bold)
                        Row {
                            repeat(r.rating) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = GoldenSun, modifier = Modifier.size(12.dp))
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))
                    Text("\"${r.comment}\"", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Product ID pointer: #${r.productId} • E-mail node: ${r.userEmail}", fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (!r.isApproved) {
                            Button(
                                onClick = { viewModel.adminApproveReview(r.id) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text("Approve review", fontSize = 11.sp)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }

                        OutlinedButton(
                            onClick = { viewModel.adminDisapproveOrDeleteReview(r.id) },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Purge/Ban comment", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: USER MODERATOR BLACKLIST
// ==========================================
@Composable
fun AdminUserModerator(
    viewModel: CrodytoViewModel,
    users: List<User>
) {
    val normalUsersList = users.filter { it.role == "user" }

    if (normalUsersList.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No registered buyer nodes yet.")
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(normalUsersList) { u ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(u.name, fontWeight = FontWeight.Bold)
                        Text(u.email, style = MaterialTheme.typography.bodySmall)
                    }

                    Button(
                        onClick = { viewModel.adminToggleUserBlocked(u.email, u.isBlocked) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (u.isBlocked) Color.Green else MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(if (u.isBlocked) "Unblock/Restore" else "Ban/Block User")
                    }
                }
            }
        }
    }
}

// ==========================================
// ADMIN SUB-SECTION: COUPON & DISCOUNTS
// ==========================================
@Composable
fun AdminCouponManager(
    viewModel: CrodytoViewModel,
    coupons: List<Coupon>
) {
    var cCode by remember { mutableStateOf("") }
    var cPercent by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Apply Coupon Registry Node", fontWeight = FontWeight.Bold)

                OutlinedTextField(value = cCode, onValueChange = { cCode = it }, label = { Text("Coupon Code (E.g. SUMMER45)") })
                OutlinedTextField(value = cPercent, onValueChange = { cPercent = it }, label = { Text("Percentage discount (1-99)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))

                Button(
                    onClick = {
                        val pInt = cPercent.toIntOrNull()
                        if (pInt != null && cCode.isNotEmpty()) {
                            viewModel.adminSaveCoupon(cCode, pInt)
                            cCode = ""
                            cPercent = ""
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Inject Coupon code")
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text("Active Coupons list:", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            items(coupons) { coup ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(coup.code, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("Discount factor: ${coup.discountPercent}% OFF", style = MaterialTheme.typography.bodySmall)
                        }

                        IconButton(onClick = { viewModel.adminDeleteCoupon(coup.code) }) {
                            Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}
