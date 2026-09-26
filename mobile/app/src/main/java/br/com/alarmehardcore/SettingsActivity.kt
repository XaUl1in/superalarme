package br.com.alarmehardcore

import android.Manifest
import android.app.Activity
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class SettingsActivity : Activity() {
    private lateinit var homeRequest: ManualLocation
    private lateinit var zoneList: LinearLayout
    private var pendingName = ""
    private lateinit var homeStatus: TextView
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val state = AlarmState(this)
        val page = NightUi.page(this)
        page.addView(NightUi.button(this, "‹  Voltar") { finish() })
        page.addView(NightUi.text(this, "Ajustes", 30f))
        page.addView(NightUi.text(this, "Prepare uma vez. Acorde todos os dias.", 14f, NightUi.muted))
        page.addView(NightUi.text(this, "Computador", 22f))
        page.addView(NightUi.text(this, if (state.secret.isEmpty()) "Cole a chave exibida no programa do PC." else "Computador pareado. Sua chave está salva.", 14f, NightUi.muted))
        val secret = NightUi.input(this, "Chave de pareamento").apply {
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            setText(state.secret)
            importantForAutofill = android.view.View.IMPORTANT_FOR_AUTOFILL_NO_EXCLUDE_DESCENDANTS
        }
        page.addView(secret)
        page.addView(NightUi.button(this, "Salvar chave", true) {
            runCatching { TimePin.decode(secret.text.toString()) }.onSuccess {
                state.secret = secret.text.toString().filterNot { it.isWhitespace() }.uppercase()
                AlarmScheduler.reschedule(this); toast("Chave salva.")
            }.onFailure { toast(it.message ?: "Chave inválida") }
        })
        page.addView(NightUi.text(this, "Zonas seguras", 22f))
        page.addView(NightUi.text(this, "O alarme sempre pede o PIN. Ao tocar em Não estou em casa, o GPS verifica todas as zonas salvas: a menos de 150 m de qualquer uma, o PC continua obrigatório.", 14f, NightUi.muted))
        val zoneName = NightUi.input(this, "Nome: Casa, Trabalho…").apply { setSingleLine() }
        page.addView(zoneName)
        homeStatus = NightUi.text(this, "", 14f, NightUi.muted); page.addView(homeStatus)
        homeRequest = ManualLocation(this) { location ->
            if (location == null) homeStatus.text = "Sem posição disponível. Nenhuma zona foi alterada."
            else runCatching { SafeZones(this).add(pendingName, location) }.onSuccess {
                homeStatus.text = "Zona salva."; zoneName.text.clear(); renderZones()
            }.onFailure { homeStatus.text = "Não foi possível salvar a zona." }
        }
        page.addView(NightUi.button(this, "Salvar localização atual", true) {
            if (!homeRequest.busy) {
                pendingName = zoneName.text.toString().trim()
                if (pendingName.isEmpty()) toast("Dê um nome à zona.")
                else { homeStatus.text = "Obtendo localização…"; homeRequest.start() }
            }
        })
        zoneList = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        page.addView(zoneList); renderZones()
        page.addView(NightUi.text(this, "Sincronizar com o PC", 22f))
        val pc = NightUi.input(this, "IP local do PC, ex.: 192.168.1.20").apply {
            setSingleLine(); setText(state.prefs.getString("pcAddress", ""))
        }
        page.addView(pc)
        page.addView(NightUi.text(this, "Abra o programa no PC. A ofensiva é enviada à porta 8080 usando a chave já pareada. O PIN continua offline.", 13f, NightUi.muted))
        page.addView(NightUi.button(this, "Salvar IP e sincronizar") {
            val address = pc.text.toString().trim()
            if (address.isNotEmpty() && !SyncProtocol.isLocalAddress(address)) toast("Informe um IPv4 de rede local válido.")
            else {
                state.prefs.edit().putString("pcAddress", address).commit()
                StreakSync.send(this)
                toast(if (address.isEmpty()) "Sincronização desativada." else "Envio solicitado. Confira o painel do PC.")
            }
        })
        page.addView(NightUi.button(this, "Ver status da sincronização") {
            toast(state.prefs.getString("syncStatus", "Nenhum envio realizado.")!!)
        })
        page.addView(NightUi.text(this, "Permissões", 22f))
        page.addView(NightUi.text(this, "Necessárias para tocar no horário e aparecer na tela bloqueada.", 14f, NightUi.muted))
        page.addView(NightUi.button(this, "Notificações   ›") {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED)
                requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            else open(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        })
        page.addView(NightUi.button(this, "Alarmes exatos   ›") {
            if (Build.VERSION.SDK_INT >= 31) open(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:$packageName")))
            else toast("Já permitido neste Android.")
        })
        page.addView(NightUi.button(this, "Tela cheia   ›") {
            if (Build.VERSION.SDK_INT >= 34) open(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
            else toast("Já permitido neste Android.")
        })
        page.addView(NightUi.text(this, "Despertar progressivo", 22f))
        page.addView(NightUi.text(this, "0–5 min · Zen: ondas suaves, volume e brilho crescentes.\n5–8 min · Hardcore: som escolhido no alarme.\nApós 8 min · Punição: sirene, volume máximo e sobreposição, se autorizada.", 15f, NightUi.muted))
        page.addView(NightUi.text(this, "O brilho muda apenas na tela do despertador. O volume anterior é restaurado ao encerrar. O PIN continua obrigatório em casa.", 13f, NightUi.muted))
        page.addView(NightUi.button(this, "Testar em 10 segundos") {
            val problem = AlarmReadiness.problem(this)
            if (problem != null) toast(problem) else { AlarmScheduler.test(this); toast("Teste agendado. Não altera alarmes salvos nem a ofensiva.") }
        })
        page.addView(NightUi.button(this, "Cancelar teste pendente") {
            state.prefs.edit().remove("testAt").commit(); AlarmScheduler.reschedule(this); toast("Teste cancelado.")
        })
        page.addView(NightUi.text(this, "Proteções adicionais", 22f))
        page.addView(NightUi.button(this, "Sobreposição   ›") { open(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))) })
        page.addView(NightUi.button(this, "Ativar administrador   ›") {
            open(Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
                .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, ComponentName(this, AlarmAdminReceiver::class.java))
                .putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Exige desativar esta autorização antes de desinstalar."))
        })
        page.addView(NightUi.button(this, "Remover administrador") {
            getSystemService(DevicePolicyManager::class.java).removeActiveAdmin(ComponentName(this, AlarmAdminReceiver::class.java))
            toast("Remoção solicitada.")
        })
        page.addView(NightUi.text(this, "O Android permite desligar o aparelho e forçar a parada. Essas proteções são opcionais.", 12f, NightUi.muted))
    }
    override fun onResume() {
        super.onResume()
        if (AlarmState(this).activeSince > 0) { startActivity(Intent(this, AlarmActivity::class.java)); finish() }
    }
    private fun open(intent: Intent) { runCatching { startActivity(intent) }.onFailure { toast("Opção não disponível neste aparelho.") } }
    private fun renderZones() {
        zoneList.removeAllViews()
        val zones = SafeZones(this).all()
        if (zones.isEmpty()) zoneList.addView(NightUi.text(this, "Nenhuma zona segura cadastrada.", 14f, NightUi.muted))
        zones.forEach { zone ->
            zoneList.addView(NightUi.panel(this).apply {
                addView(NightUi.text(this@SettingsActivity, zone.name, 18f))
                addView(NightUi.text(this@SettingsActivity, String.format(java.util.Locale.US, "%.5f, %.5f", zone.latitude, zone.longitude), 12f, NightUi.muted))
                addView(NightUi.button(this@SettingsActivity, "Excluir zona") {
                    android.app.AlertDialog.Builder(this@SettingsActivity).setTitle("Excluir ${zone.name}?")
                        .setNegativeButton("Cancelar", null).setPositiveButton("Excluir") { _, _ ->
                            SafeZones(this@SettingsActivity).remove(zone.id); renderZones()
                        }.show()
                })
            })
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        homeRequest.permissionResult(requestCode)
    }
    @Deprecated("Compatibilidade com resolução de localização")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        homeRequest.resolutionResult(requestCode, resultCode)
    }
    override fun onDestroy() { homeRequest.close(); super.onDestroy() }
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
