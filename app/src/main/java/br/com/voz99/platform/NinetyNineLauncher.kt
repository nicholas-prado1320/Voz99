package br.com.voz99.platform

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

object NinetyNineLauncher {
    const val PACKAGE = "com.taxis99"

    fun launch(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(PACKAGE)
            if (intent != null) {
                context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            }
            // Em aparelho sem a 99 instalada, oferece a pagina oficial de instalacao.
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$PACKAGE"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (_: Exception) {
            try {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$PACKAGE"))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            } catch (_: Exception) {
                Toast.makeText(context, "Não foi possível abrir a 99.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
