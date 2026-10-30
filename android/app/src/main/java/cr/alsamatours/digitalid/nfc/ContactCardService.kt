package cr.alsamatours.digitalid.nfc

import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle

/**
 * ISO 14443-4 / NFC Forum Type 4 Tag emulation for compatible NFC readers.
 * Does NOT implement Android Beam or promise universal phone-to-phone sharing.
 * The reader must select the NDEF application and issue APDU commands.
 */
class ContactCardService : HostApduService() {
    private var selectedFile: Int = 0
    private val success = byteArrayOf(0x90.toByte(), 0x00)
    private val fileNotFound = byteArrayOf(0x6A.toByte(), 0x82.toByte())
    private val invalid = byteArrayOf(0x6A.toByte(), 0x86.toByte())
    private fun apduData(bytes: ByteArray) = bytes + success

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        val cmd = commandApdu ?: return invalid
        if (cmd.size < 4) return invalid
        val ins = cmd[1].toInt() and 0xFF
        if (ins == 0xA4 && cmd.size >= 7) {
            val lc = cmd[4].toInt() and 0xFF
            if (cmd.size < 5 + lc) return invalid
            val selection = cmd.copyOfRange(5, 5 + lc)
            if (selection.contentEquals(byteArrayOf(0xD2.toByte(),0x76,0x00,0x00,0x85.toByte(),0x01,0x01))) {
                selectedFile = 0
                return success
            }
            if (selection.contentEquals(byteArrayOf(0xE1.toByte(),0x03))) { selectedFile = 1; return success }
            if (selection.contentEquals(byteArrayOf(0xE1.toByte(),0x04))) { selectedFile = 2; return success }
            return fileNotFound
        }
        if (ins == 0xB0 && cmd.size >= 5) {
            val offset = ((cmd[2].toInt() and 0xFF) shl 8) or (cmd[3].toInt() and 0xFF)
            val length = (cmd[4].toInt() and 0xFF).let { if (it == 0) 256 else it }
            val content = when (selectedFile) {
                1 -> byteArrayOf(0x00,0x0F,0x20,0x00,0x3B,0x00,0x34,0x04,0x06,0xE1.toByte(),0x04,0x10,0x00,0x00,0x00)
                2 -> {
                    val profileUrl = getSharedPreferences("alsama_config", MODE_PRIVATE).getString("nfc_url", "").orEmpty()
                    if (!profileUrl.startsWith("https://")) return fileNotFound
                    val ndef = NdefMessage(arrayOf(NdefRecord.createUri(profileUrl))).toByteArray()
                    if (ndef.size > 4094) return invalid
                    byteArrayOf((ndef.size shr 8).toByte(), ndef.size.toByte()) + ndef
                }
                else -> return fileNotFound
            }
            if (offset >= content.size) return invalid
            return apduData(content.copyOfRange(offset, minOf(content.size, offset + length)))
        }
        return invalid
    }
    override fun onDeactivated(reason: Int) { selectedFile = 0 }
}