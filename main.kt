import kotlin.math.floor

// ---------- Domain ----------
enum class Category { FRUITS_VEG, BAKERY, DAIRY, BEVERAGES, HOUSEHOLD }

data class Product(
    val id: Int,
    val name: String,
    val category: Category,
    val price: Double,
    var stock: Int
)

data class CartItem(val product: Product, var qty: Int)

// ---------- Coupons ----------
sealed class Coupon(val code: String, val title: String) {
    open fun discount(cart: Cart): Pair<Double, String> = 0.0 to "No discount"

    class PercentOff(code: String, private val percent: Int, private val minSpend: Double) :
        Coupon(code, "$percent% off over ₹${minSpend.toMoney()}") {
        override fun discount(cart: Cart): Pair<Double, String> {
            val sub = cart.subtotal()
            return if (sub >= minSpend) (sub * percent / 100.0) to "$percent% off" else
                0.0 to "Spend ₹${minSpend.toMoney()} to use $code"
        }
    }

    class AmountOff(code: String, private val amount: Double, private val minSpend: Double) :
        Coupon(code, "₹${amount.toMoney()} off over ₹${minSpend.toMoney()}") {
        override fun discount(cart: Cart): Pair<Double, String> {
            val sub = cart.subtotal()
            return if (sub >= minSpend) amount to "₹${amount.toMoney()} off" else
                0.0 to "Spend ₹${minSpend.toMoney()} to use $code"
        }
    }

    object BogoBread : Coupon("BOGO-BREAD", "Buy 1 Get 1 Free: Bread") {
        override fun discount(cart: Cart): Pair<Double, String> {
            val breadItem = cart.listItems().find { it.product.name.contains("Bread", ignoreCase = true) }
                ?: return 0.0 to "Add Bread to use $code"
            val freeUnits = breadItem.qty / 2
            val d = freeUnits * breadItem.product.price
            return d to "BOGO: ${breadItem.product.name} free x$freeUnits"
        }
    }
}

fun resolveCoupon(code: String): Coupon? = when (code.trim().uppercase()) {
    "TESCO10" -> Coupon.PercentOff("TESCO10", 10, 500.0)
    "SAVE50" -> Coupon.AmountOff("SAVE50", 50.0, 300.0)
    "BOGO-BREAD" -> Coupon.BogoBread
    else -> null
}

// ---------- Store ----------
object Store {
    val inventory = mutableListOf(
        Product(101, "Bananas (1kg)", Category.FRUITS_VEG, 68.0, 40),
        Product(102, "Apples (1kg)", Category.FRUITS_VEG, 155.0, 25),
        Product(103, "Tomatoes (1kg)", Category.FRUITS_VEG, 55.0, 30),

        Product(201, "Whole Wheat Bread", Category.BAKERY, 45.0, 50),
        Product(202, "Croissant (Pack of 4)", Category.BAKERY, 120.0, 20),

        Product(301, "Milk 1L", Category.DAIRY, 64.0, 60),
        Product(302, "Cheddar Cheese 200g", Category.DAIRY, 210.0, 15),
        Product(303, "Eggs (12 pack)", Category.DAIRY, 110.0, 25),

        Product(401, "Assam Tea 250g", Category.BEVERAGES, 180.0, 18),
        Product(402, "Instant Coffee 100g", Category.BEVERAGES, 290.0, 12),
        Product(403, "Coke 1.25L", Category.BEVERAGES, 85.0, 35),

        Product(501, "Dish Detergent 500ml", Category.HOUSEHOLD, 99.0, 28),
        Product(502, "Laundry Liquid 1L", Category.HOUSEHOLD, 229.0, 16),
    )

    fun byId(id: Int) = inventory.find { it.id == id }
    fun byCategory(cat: Category) = inventory.filter { it.category == cat }
    fun search(q: String) = inventory.filter { it.name.contains(q, ignoreCase = true) }
    fun sortedByPrice(asc: Boolean) =
        if (asc) inventory.sortedBy { it.price } else inventory.sortedByDescending { it.price }
}

// ---------- Cart ----------
class Cart {
    private val items = mutableMapOf<Int, CartItem>()
    var coupon: Coupon? = null

    fun add(p: Product, qty: Int = 1) {
        if (qty <= 0) return
        if (p.stock <= 0) { println("❌ Out of stock: ${p.name}"); return }
        val addQty = qty.coerceAtMost(p.stock)
        p.stock -= addQty
        val existing = items[p.id]
        if (existing == null) items[p.id] = CartItem(p, addQty) else existing.qty += addQty
        println(" Added ${p.name} x$addQty")
    }

    fun remove(p: Product, qty: Int = 1) {
        val item = items[p.id] ?: return println("Item not in cart.")
        val take = qty.coerceAtMost(item.qty)
        item.qty -= take
        p.stock += take
        if (item.qty <= 0) items.remove(p.id)
        println("➖ Removed ${p.name} x$take")
    }

    fun listItems(): List<CartItem> = items.values.sortedBy { it.product.name }

    fun subtotal(): Double = items.values.sumOf { it.product.price * it.qty }

    fun clearCancel() { // put items back to stock (cancel order)
        items.values.forEach { it.product.stock += it.qty }
        items.clear()
        coupon = null
    }

    fun afterCheckoutReset() { // do NOT restock (successful purchase)
        items.clear()
        coupon = null
    }
}

// ---------- Utilities ----------
fun Double.toMoney(): String = "%.2f".format(this)
fun Product.tag(): String = "#$id  ${name}  —  ₹${price.toMoney()}  (Stock: $stock)"
fun readLineTrim(prompt: String): String {
    print(prompt)
    return readlnOrNull()?.trim().orEmpty()
}
fun readInt(prompt: String): Int? = readLineTrim(prompt).toIntOrNull()
fun pressEnterToContinue() { print("\n(Press Enter to continue) "); readlnOrNull() }

// Generic chooser to show off generics + lambdas
fun <T> chooseFromList(title: String, items: List<T>, labeler: (T) -> String): T? {
    if (items.isEmpty()) { println("No items."); return null }
    println("\n-- $title --")
    items.forEachIndexed { i, t -> println("${i + 1}. ${labeler(t)}") }
    val idx = readInt("Choose (1-${items.size}) or 0 to cancel: ") ?: return null
    if (idx == 0) return null
    return items.getOrNull(idx - 1)
}

// ---------- Receipt ----------
fun printReceipt(cart: Cart) {
    val sub = cart.subtotal()
    val (disc, discLabel) = cart.coupon?.discount(cart) ?: 0.0 to ""
    val afterDiscount = (sub - disc).coerceAtLeast(0.0)
    val taxRate = 0.05 // demo
    val tax = afterDiscount * taxRate
    val total = afterDiscount + tax
    val pointsEarned = floor(total / 100) * 10 // 10 pts per ₹100

    println("\n================= TESCO TROLLEY RECEIPT =================")
    cart.listItems().forEach {
        val line = it.product.price * it.qty
        println("${it.product.name}  x${it.qty}  @ ₹${it.product.price.toMoney()}  =  ₹${line.toMoney()}")
    }
    println("---------------------------------------------------------")
    println("Subtotal:             ₹${sub.toMoney()}")
    if (disc > 0) println("${cart.coupon!!.code} ($discLabel):  -₹${disc.toMoney()}")
    println("GST (5%):             ₹${tax.toMoney()}")
    println("TOTAL:                ₹${total.toMoney()}")
    println("Loyalty Points:       +${pointsEarned.toInt()}")
    println("=========================================================\n")
}

// ---------- Menus ----------
fun mainMenu() {
    val cart = Cart()
    loop@ while (true) {
        println(
            """
            
            =================== Tesco Trolley (Console) ===================
            1) Browse by category
            2) Search products
            3) Sort products by price (asc/desc)
            4) View cart / modify
            5) Apply coupon (TESCO10 / SAVE50 / BOGO-BREAD)
            6) Checkout
            7) Cancel order & empty cart
            0) Exit
            ----------------------------------------------------------------
        """.trimIndent()
        )

        when (readInt("Choose: ") ?: -1) {
            1 -> browseByCategory(cart)
            2 -> searchProducts(cart)
            3 -> sortProducts()
            4 -> viewCart(cart)
            5 -> applyCoupon(cart)
            6 -> {
                if (cart.listItems().isEmpty()) { println("Cart is empty."); continue }
                printReceipt(cart)
                println("Thanks for shopping! ")
                cart.afterCheckoutReset()
                pressEnterToContinue()
            }
            7 -> {
                cart.clearCancel()
                println("Cart cleared. (Order cancelled) ")
                pressEnterToContinue()
            }
            0 -> { println("Goodbye! 👋"); break@loop }
            else -> println("Invalid choice.")
        }
    }
}

fun browseByCategory(cart: Cart) {
    val cat = chooseFromList("Categories", Category.values().toList()) { it.name.replace('_', ' ') } ?: return
    val list = Store.byCategory(cat)
    val p = chooseFromList("Products in ${cat.name.replace('_', ' ')}", list) { it.tag() } ?: return
    val qty = readInt("Qty to add: ")?.coerceAtLeast(1) ?: 1
    cart.add(p, qty)
    pressEnterToContinue()
}

fun searchProducts(cart: Cart) {
    val q = readLineTrim("Search text: ")
    val list = Store.search(q)
    if (list.isEmpty()) { println("No matches."); pressEnterToContinue(); return }
    val p = chooseFromList("Search Results", list) { it.tag() } ?: return
    val qty = readInt("Qty to add: ")?.coerceAtLeast(1) ?: 1
    cart.add(p, qty)
    pressEnterToContinue()
}

fun sortProducts() {
    val asc = readLineTrim("Sort by price ascending? (y/n): ").equals("y", true)
    val list = Store.sortedByPrice(asc)
    println("\n-- All Products (${if (asc) "Low→High" else "High→Low"}) --")
    list.forEach { println(it.tag()) }
    pressEnterToContinue()
}

fun viewCart(cart: Cart) {
    val items = cart.listItems()
    if (items.isEmpty()) { println("Cart is empty."); pressEnterToContinue(); return }
    println("\n-- Your Cart --")
    items.forEachIndexed { i, it ->
        println("${i + 1}. ${it.product.name} x${it.qty} — ₹${(it.product.price * it.qty).toMoney()}")
    }
    println("Subtotal: ₹${cart.subtotal().toMoney()}")

    when (readInt("1) Remove item  2) Change qty  0) Back: ") ?: 0) {
        1 -> {
            val idx = readInt("Which item #: ")?.minus(1) ?: return
            val it = items.getOrNull(idx) ?: return
            val q = readInt("Remove qty: ")?.coerceAtLeast(1) ?: 1
            cart.remove(it.product, q)
        }
        2 -> {
            val idx = readInt("Which item #: ")?.minus(1) ?: return
            val it = items.getOrNull(idx) ?: return
            val newQty = readInt("New qty (0 to remove): ") ?: return
            when {
                newQty < it.qty -> cart.remove(it.product, it.qty - newQty)
                newQty > it.qty -> cart.add(it.product, newQty - it.qty)
                else -> println("No change.")
            }
        }
    }
    pressEnterToContinue()
}

fun applyCoupon(cart: Cart) {
    val code = readLineTrim("Enter coupon code: ")
    val c = resolveCoupon(code)
    if (c == null) {
        println("❌ Unknown code.")
    } else {
        cart.coupon = c
        val (d, msg) = c.discount(cart)
        println("Applied: ${c.code} — ${c.title}")
        println("Current discount (based on cart): ₹${d.toMoney()} ($msg)")
    }
    pressEnterToContinue()
}

// ---------- Entry ----------
fun main() = mainMenu()
