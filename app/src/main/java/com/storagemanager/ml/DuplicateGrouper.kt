package com.storagemanager.ml

/**
 * Perceptual hash listesini duplike gruplarına ayırır.
 *
 * Naif yaklaşım her çifti karşılaştırır (O(N²)); 10.000 fotoğrafta ~50 milyon karşılaştırma.
 * Bunun yerine:
 *  1. Birebir aynı hash'ler tek adımda birleştirilir (O(N)).
 *  2. Kalan **benzersiz** hash'ler bantlara göre kovalanır — Hamming mesafesi eşiği
 *     aşmayan her çift en az bir bantta aynı anahtara düşer, dolayısıyla sadece
 *     aynı kovadaki adaylar karşılaştırılır.
 *
 * Android bağımlılığı yoktur; birim testlerinde doğrudan çalıştırılabilir.
 */
object DuplicateGrouper {

    /**
     * @param hashes Fotoğrafların dHash değerleri (indeks = fotoğrafın liste sırası)
     * @param threshold Duplike sayılmak için izin verilen en büyük Hamming mesafesi
     * @return En az iki üyesi olan grupların indeks listeleri; her grup artan sırada
     */
    fun group(hashes: LongArray, threshold: Int = DuplicateDetector.DEFAULT_THRESHOLD): List<List<Int>> {
        if (hashes.size < 2) return emptyList()

        val unionFind = UnionFind(hashes.size)

        // 1. Aynı hash'e sahip fotoğrafları doğrudan birleştir; benzersiz hash temsilcilerini topla
        val firstIndexOfHash = HashMap<Long, Int>(hashes.size)
        for (i in hashes.indices) {
            val existing = firstIndexOfHash.putIfAbsent(hashes[i], i)
            if (existing != null) unionFind.union(existing, i)
        }

        // 2. Benzersiz hash'leri bant anahtarlarına göre kovala
        val buckets = HashMap<Long, MutableList<Int>>()
        for ((hash, index) in firstIndexOfHash) {
            for (key in DuplicateDetector.bandKeys(hash)) {
                buckets.getOrPut(key) { mutableListOf() }.add(index)
            }
        }

        // 3. Sadece aynı kovaya düşen adayları karşılaştır
        for (candidates in buckets.values) {
            if (candidates.size < 2) continue
            for (i in candidates.indices) {
                val a = candidates[i]
                for (j in i + 1 until candidates.size) {
                    val b = candidates[j]
                    if (unionFind.connected(a, b)) continue
                    if (java.lang.Long.bitCount(hashes[a] xor hashes[b]) <= threshold) {
                        unionFind.union(a, b)
                    }
                }
            }
        }

        val groups = LinkedHashMap<Int, MutableList<Int>>()
        for (i in hashes.indices) {
            groups.getOrPut(unionFind.find(i)) { mutableListOf() }.add(i)
        }
        return groups.values.filter { it.size >= 2 }
    }

    /** Yol sıkıştırmalı ve rank'li birleşim-bulma yapısı. */
    private class UnionFind(size: Int) {
        private val parent = IntArray(size) { it }
        private val rank = ByteArray(size)

        fun find(i: Int): Int {
            var root = i
            while (parent[root] != root) root = parent[root]
            var current = i
            while (current != root) {
                val next = parent[current]
                parent[current] = root
                current = next
            }
            return root
        }

        fun connected(a: Int, b: Int): Boolean = find(a) == find(b)

        fun union(a: Int, b: Int) {
            val rootA = find(a)
            val rootB = find(b)
            if (rootA == rootB) return
            when {
                rank[rootA] < rank[rootB] -> parent[rootA] = rootB
                rank[rootA] > rank[rootB] -> parent[rootB] = rootA
                else -> {
                    parent[rootB] = rootA
                    rank[rootA]++
                }
            }
        }
    }
}
