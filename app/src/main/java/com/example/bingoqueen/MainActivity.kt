// This is the main logic file for your screen, typically located in app/src/main/java/com/example/princessbingo/
package com.example.bingoqueen

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.example.princessbingo.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    // --- View Binding & Firebase ---
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // --- State Management ---
    private var userId: String? = null
    private var gameId: String? = null
    private var gameData: Map<String, Any>? = null

    // --- App Setup ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        auth = Firebase.auth
        db = Firebase.firestore

        // Sign in user anonymously and set up UI
        signInAnonymously()
        setupClickListeners()
    }

    private fun signInAnonymously() {
        lifecycleScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                userId = auth.currentUser?.uid
                Toast.makeText(this@MainActivity, "Authenticated!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.createGameButton.setOnClickListener { createGame() }
        binding.joinGameButton.setOnClickListener { joinGame() }
        binding.callNumberButton.setOnClickListener { callNumber() }
    }

    // --- Game Logic ---
    private fun createGame() {
        if (userId == null) return
        lifecycleScope.launch {
            val newGameId = (1..6).map { ('A'..'Z').random() }.joinToString("")
            val playerBoard = generateBoard()
            val initialMarked = List(5) { MutableList(5) { false } }.also { it[2][2] = true }

            val newGame = hashMapOf(
                "players" to hashMapOf(
                    userId to hashMapOf(
                        "name" to "Princess Player 1",
                        "board" to playerBoard,
                        "marked" to initialMarked
                    )
                ),
                "calledNumbers" to emptyList<Int>(),
                "currentPlayer" to userId,
                "winner" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )

            try {
                db.collection("bingoGames").document(newGameId).set(newGame).await()
                gameId = newGameId
                listenForGameUpdates()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error creating game: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun joinGame() {
        val inputGameId = binding.gameIdEditText.text.toString().uppercase()
        if (userId == null || inputGameId.isBlank()) {
            Toast.makeText(this, "Please enter a valid Game ID", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val gameRef = db.collection("bingoGames").document(inputGameId)
            try {
                val doc = gameRef.get().await()
                if (!doc.exists()) {
                    Toast.makeText(this@MainActivity, "Game not found", Toast.LENGTH_SHORT).show()
                    return
                }

                val players = doc.get("players") as? Map<String, Any> ?: emptyMap()
                if (players.size >= 2 && !players.containsKey(userId)) {
                    Toast.makeText(this@MainActivity, "Game is full", Toast.LENGTH_SHORT).show()
                    return
                }

                if (!players.containsKey(userId)) {
                    val playerBoard = generateBoard()
                    val initialMarked = List(5) { MutableList(5) { false } }.also { it[2][2] = true }
                    gameRef.update(
                        "players.$userId", hashMapOf(
                            "name" to "Challenger Player 2",
                            "board" to playerBoard,
                            "marked" to initialMarked
                        )
                    ).await()
                }

                gameId = inputGameId
                listenForGameUpdates()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error joining game: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun listenForGameUpdates() {
        if (gameId == null) return
        db.collection("bingoGames").document(gameId!!).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                gameData = snapshot.data
                updateUI()
            } else {
                Toast.makeText(this, "Game data not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun callNumber() {
        val players = gameData?.get("players") as? Map<String, Any> ?: return
        val currentPlayer = gameData?.get("currentPlayer") as? String ?: return
        if (currentPlayer != userId || players.size < 2) return

        lifecycleScope.launch {
            val calledNumbers = gameData?.get("calledNumbers") as? List<Long> ?: emptyList()
            val availableNumbers = (1..75).filter { !calledNumbers.contains(it.toLong()) }
            if (availableNumbers.isEmpty()) {
                Toast.makeText(this@MainActivity, "All numbers called!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val newNumber = availableNumbers.random()
            val nextPlayerId = players.keys.find { it != userId }

            db.collection("bingoGames").document(gameId!!)
                .update(
                    "calledNumbers", FieldValue.arrayUnion(newNumber),
                    "currentPlayer", nextPlayerId
                ).await()
        }
    }

    private fun markCell(row: Int, col: Int) {
        // ... Implementation for marking a cell and checking for a win
        // This would involve updating the 'marked' array for the player in Firestore
        // and then calling a 'checkForWin' function.
    }

    // --- UI Update Logic ---
    private fun updateUI() {
        if (gameData == null) return
        
        // Switch to game view
        binding.lobbyGroup.visibility = View.GONE
        binding.gameGroup.visibility = View.VISIBLE

        binding.gameIdTextView.text = "Game ID: $gameId"
        
        // Update status text
        val winner = gameData?.get("winner") as? String
        if (winner != null) {
            val players = gameData?.get("players") as Map<String, Map<String, Any>>
            val winnerName = players[winner]?.get("name")
            binding.statusTextView.text = "🎉 $winnerName wins! 🎉"
        } else {
            val currentPlayer = gameData?.get("currentPlayer") as? String
            binding.statusTextView.text = if (currentPlayer == userId) "Your turn!" else "Waiting for opponent..."
        }

        // Update called numbers display
        val calledNumbers = gameData?.get("calledNumbers") as? List<Long> ?: emptyList()
        binding.calledNumbersLayout.removeAllViews()
        calledNumbers.forEach { num ->
             val textView = TextView(this).apply {
                text = num.toString()
                // Add styling here
             }
             binding.calledNumbersLayout.addView(textView)
        }

        // Update Bingo Board
        drawBingoBoard()
    }

    private fun drawBingoBoard() {
        val players = gameData?.get("players") as? Map<String, Any> ?: return
        val myPlayerData = players[userId] as? Map<String, Any> ?: return
        val board = myPlayerData["board"] as? List<List<Long>> ?: return
        val marked = myPlayerData["marked"] as? List<List<Boolean>> ?: return

        binding.bingoBoardGridLayout.removeAllViews()
        binding.bingoBoardGridLayout.alignmentMode = androidx.gridlayout.widget.GridLayout.ALIGN_BOUNDS
        
        for (r in 0..4) {
            for (c in 0..4) {
                val cell = Button(this)
                val number = board[r][c]
                cell.text = if (r == 2 && c == 2) "FREE" else number.toString()
                
                if (marked[r][c]) {
                    cell.setBackgroundColor(ContextCompat.getColor(this, R.color.pink_400))
                } else {
                    cell.setBackgroundColor(ContextCompat.getColor(this, R.color.white))
                }

                val params = androidx.gridlayout.widget.GridLayout.LayoutParams(
                    androidx.gridlayout.widget.GridLayout.spec(r, 1f),
                    androidx.gridlayout.widget.GridLayout.spec(c, 1f)
                ).apply {
                    width = 0
                    height = 0
                    setMargins(4, 4, 4, 4)
                }
                cell.layoutParams = params
                cell.setOnClickListener { markCell(r, c) }
                binding.bingoBoardGridLayout.addView(cell)
            }
        }
    }

    // --- Helper Functions ---
    private fun generateBoard(): List<List<Int>> {
        // This logic is the same as the React version, translated to Kotlin.
        val board = List(5) { MutableList(5) { 0 } }
        val ranges = mapOf(0 to 15, 1 to 15, 2 to 15, 3 to 15, 4 to 15)
        val offsets = mapOf(0 to 0, 1 to 15, 2 to 30, 3 to 45, 4 to 60)

        for (col in 0..4) {
            val usedNumbers = mutableSetOf<Int>()
            for (row in 0..4) {
                if (col == 2 && row == 2) continue
                var num: Int
                do {
                    num = (1..ranges.getValue(col)).random() + offsets.getValue(col)
                } while (usedNumbers.contains(num))
                usedNumbers.add(num)
                board[row][col] = num
            }
        }
        return board
    }
}

// This is the main logic file for your screen, typically located in app/src/main/java/com/example/bingoqueen/
package com.example.bingoqueen

import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.children
import androidx.lifecycle.lifecycleScope
import com.example.bingoqueen.databinding.ActivityMainBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MainActivity : AppCompatActivity() {

    // --- View Binding & Firebase ---
    private lateinit var binding: ActivityMainBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore

    // --- State Management ---
    private var userId: String? = null
    private var gameId: String? = null
    private var gameData: Map<String, Any>? = null

    // --- App Setup ---
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize Firebase
        auth = Firebase.auth
        db = Firebase.firestore

        // Sign in user anonymously and set up UI
        signInAnonymously()
        setupClickListeners()
    }

    private fun signInAnonymously() {
        lifecycleScope.launch {
            try {
                if (auth.currentUser == null) {
                    auth.signInAnonymously().await()
                }
                userId = auth.currentUser?.uid
                Toast.makeText(this@MainActivity, "Authenticated as Queen!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Authentication failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun setupClickListeners() {
        binding.createGameButton.setOnClickListener { createGame() }
        binding.joinGameButton.setOnClickListener { joinGame() }
        binding.callNumberButton.setOnClickListener { callNumber() }
    }

    // --- Game Logic ---
    private fun createGame() {
        if (userId == null) return
        lifecycleScope.launch {
            val newGameId = (1..6).map { ('A'..'Z').random() }.joinToString("")
            val playerBoard = generateBoard()
            val initialMarked = List(5) { MutableList(5) { false } }.also { it[2][2] = true }

            val newGame = hashMapOf(
                "players" to hashMapOf(
                    userId to hashMapOf(
                        "name" to "Player 1",
                        "board" to playerBoard,
                        "marked" to initialMarked
                    )
                ),
                "calledNumbers" to emptyList<Int>(),
                "currentPlayer" to userId,
                "winner" to null,
                "createdAt" to FieldValue.serverTimestamp()
            )

            try {
                db.collection("bingoGames").document(newGameId).set(newGame).await()
                gameId = newGameId
                listenForGameUpdates()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error creating game: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun joinGame() {
        val inputGameId = binding.gameIdEditText.text.toString().uppercase()
        if (userId == null || inputGameId.isBlank()) {
            Toast.makeText(this, "Please enter a valid Game ID", Toast.LENGTH_SHORT).show()
            return
        }

        lifecycleScope.launch {
            val gameRef = db.collection("bingoGames").document(inputGameId)
            try {
                val doc = gameRef.get().await()
                if (!doc.exists()) {
                    Toast.makeText(this@MainActivity, "Game not found", Toast.LENGTH_SHORT).show()
                    return
                }

                val players = doc.get("players") as? Map<String, Any> ?: emptyMap()
                if (players.size >= 2 && !players.containsKey(userId)) {
                    Toast.makeText(this@MainActivity, "Game is full", Toast.LENGTH_SHORT).show()
                    return
                }

                if (!players.containsKey(userId)) {
                    val playerBoard = generateBoard()
                    val initialMarked = List(5) { MutableList(5) { false } }.also { it[2][2] = true }
                    gameRef.update(
                        "players.$userId", hashMapOf(
                            "name" to "Player 2",
                            "board" to playerBoard,
                            "marked" to initialMarked
                        )
                    ).await()
                }

                gameId = inputGameId
                listenForGameUpdates()
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error joining game: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun listenForGameUpdates() {
        if (gameId == null) return
        db.collection("bingoGames").document(gameId!!).addSnapshotListener { snapshot, e ->
            if (e != null) {
                Toast.makeText(this, "Listen failed: ${e.message}", Toast.LENGTH_SHORT).show()
                return@addSnapshotListener
            }

            if (snapshot != null && snapshot.exists()) {
                gameData = snapshot.data
                updateUI()
            } else {
                Toast.makeText(this, "Game data not found.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun callNumber() {
        val players = gameData?.get("players") as? Map<String, Any> ?: return
        val currentPlayer = gameData?.get("currentPlayer") as? String ?: return
        val winner = gameData?.get("winner") as? String
        if (currentPlayer != userId || players.size < 2 || winner != null) return

        lifecycleScope.launch {
            val calledNumbers = gameData?.get("calledNumbers") as? List<Long> ?: emptyList()
            val availableNumbers = (1..75).filter { !calledNumbers.contains(it.toLong()) }
            if (availableNumbers.isEmpty()) {
                Toast.makeText(this@MainActivity, "All numbers called!", Toast.LENGTH_SHORT).show()
                return@launch
            }

            val newNumber = availableNumbers.random()
            val nextPlayerId = players.keys.find { it != userId }

            db.collection("bingoGames").document(gameId!!)
                .update(
                    "calledNumbers", FieldValue.arrayUnion(newNumber),
                    "currentPlayer", nextPlayerId
                ).await()
        }
    }

    private fun markCell(row: Int, col: Int) {
        if (gameData == null || userId == null) return
        val winner = gameData?.get("winner") as? String
        if (winner != null) return // Game is already over

        val players = gameData?.get("players") as? Map<String, Map<String, Any>> ?: return
        val myPlayerData = players[userId!!] ?: return
        val board = myPlayerData["board"] as? List<List<Long>> ?: return
        val marked = myPlayerData["marked"] as? List<List<Boolean>> ?: return
        val calledNumbers = gameData?.get("calledNumbers") as? List<Long> ?: return

        val number = board[row][col]
        if (number != 0L && !calledNumbers.contains(number)) {
            Toast.makeText(this, "Number $number has not been called!", Toast.LENGTH_SHORT).show()
            return
        }

        if (marked[row][col]) return // Already marked

        // Create a new mutable list to update
        val newMarked = marked.map { it.toMutableList() }.toMutableList()
        newMarked[row][col] = true

        lifecycleScope.launch {
            try {
                // Update the marked board in Firestore
                db.collection("bingoGames").document(gameId!!)
                    .update("players.$userId.marked", newMarked).await()

                // Check for a win
                if (checkForWin(newMarked)) {
                    db.collection("bingoGames").document(gameId!!).update("winner", userId).await()
                }
            } catch (e: Exception) {
                Toast.makeText(this@MainActivity, "Error marking cell: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }
    
    private fun checkForWin(markedBoard: List<List<Boolean>>): Boolean {
        // Check rows
        for (i in 0..4) {
            if (markedBoard[i].all { it }) return true
        }
        // Check columns
        for (i in 0..4) {
            if (markedBoard.all { row -> row[i] }) return true
        }
        // Check diagonals
        if ((0..4).all { markedBoard[it][it] }) return true
        if ((0..4).all { markedBoard[it][4 - it] }) return true

        return false
    }

    // --- UI Update Logic ---
    private fun updateUI() {
        if (gameData == null) return
        
        binding.lobbyGroup.visibility = View.GONE
        binding.gameGroup.visibility = View.VISIBLE

        binding.gameIdTextView.text = "Game ID: $gameId"
        
        val players = gameData?.get("players") as? Map<String, Map<String, Any>> ?: emptyMap()
        val winner = gameData?.get("winner") as? String
        if (winner != null) {
            val winnerName = players[winner]?.get("name") ?: "A player"
            binding.statusTextView.text = "🎉 $winnerName wins! 🎉"
            binding.callNumberButton.isEnabled = false
        } else {
            val currentPlayer = gameData?.get("currentPlayer") as? String
            binding.statusTextView.text = if (currentPlayer == userId) "Your turn!" else "Waiting for opponent..."
            binding.callNumberButton.isEnabled = (currentPlayer == userId && players.size == 2)
        }

        val calledNumbers = gameData?.get("calledNumbers") as? List<Long> ?: emptyList()
        binding.calledNumbersLayout.removeAllViews()
        calledNumbers.forEach { num ->
             val textView = TextView(this).apply {
                text = num.toString()
                textSize = 18f
                gravity = Gravity.CENTER
                setPadding(16, 8, 16, 8)
                // More styling can be added here
             }
             binding.calledNumbersLayout.addView(textView)
        }

        drawBingoBoard()
    }

    private fun drawBingoBoard() {
        val players = gameData?.get("players") as? Map<String, Any> ?: return
        val myPlayerData = players[userId] as? Map<String, Any> ?: return
        val board = myPlayerData["board"] as? List<List<Long>> ?: return
        val marked = myPlayerData["marked"] as? List<List<Boolean>> ?: return

        binding.bingoBoardGridLayout.removeAllViews()
        binding.bingoBoardGridLayout.alignmentMode = androidx.gridlayout.widget.GridLayout.ALIGN_BOUNDS
        
        for (r in 0..4) {
            for (c in 0..4) {
                val cell = Button(this)
                val number = board[r][c]
                cell.text = if (r == 2 && c == 2) "FREE" else number.toString()
                
                if (marked[r][c]) {
                    cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.holo_pink))
                } else {
                    cell.setBackgroundColor(ContextCompat.getColor(this, android.R.color.white))
                }

                val params = androidx.gridlayout.widget.GridLayout.LayoutParams(
                    androidx.gridlayout.widget.GridLayout.spec(r, 1f),
                    androidx.gridlayout.widget.GridLayout.spec(c, 1f)
                ).apply {
                    width = 0
                    height = 0
                    setMargins(4, 4, 4, 4)
                }
                cell.layoutParams = params
                cell.setOnClickListener { markCell(r, c) }
                binding.bingoBoardGridLayout.addView(cell)
            }
        }
    }

    // --- Helper Functions ---
    private fun generateBoard(): List<List<Long>> {
        val board = List(5) { MutableList(5) { 0L } }
        val ranges = mapOf(0 to 15, 1 to 15, 2 to 15, 3 to 15, 4 to 15)
        val offsets = mapOf(0 to 0, 1 to 15, 2 to 30, 3 to 45, 4 to 60)

        for (col in 0..4) {
            val usedNumbers = mutableSetOf<Long>()
            for (row in 0..4) {
                if (col == 2 && row == 2) continue
                var num: Long
                do {
                    num = ((1..ranges.getValue(col)).random() + offsets.getValue(col)).toLong()
                } while (usedNumbers.contains(num))
                usedNumbers.add(num)
                board[row][col] = num
            }
        }
        return board
    }
}
