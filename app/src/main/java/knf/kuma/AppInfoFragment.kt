package knf.kuma

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources.getDrawable
import androidx.core.net.toUri
import com.afollestad.materialdialogs.MaterialDialog
import com.danielstone.materialaboutlibrary.ConvenienceBuilder
import com.danielstone.materialaboutlibrary.MaterialAboutFragment
import com.danielstone.materialaboutlibrary.items.MaterialAboutActionItem
import com.danielstone.materialaboutlibrary.items.MaterialAboutTitleItem
import com.danielstone.materialaboutlibrary.model.MaterialAboutCard
import com.danielstone.materialaboutlibrary.model.MaterialAboutList
import knf.kuma.commons.DesignUtils
import knf.kuma.commons.EAUnlockActivity
import knf.kuma.commons.safeShow

/** Acerca de: UKIKU AniList es un fork de UKIKU (MIT), por eso se conservan los créditos y la licencia original. */
class AppInfoFragment : MaterialAboutFragment() {

    private val isFlat: Boolean by lazy { requireArguments().getBoolean("isFlat", true) }

    override fun getMaterialAboutList(context: Context): MaterialAboutList {
        val infoCard = MaterialAboutCard.Builder().outline(isFlat)
        infoCard.addItem(
            MaterialAboutTitleItem.Builder()
                .text("UKIKU AniList")
                .desc("Fork no oficial de UKIKU con sincronización automática con AniList: importa tu biblioteca y registra cada episodio que terminas. Sin anuncios.")
                .icon(R.mipmap.ic_launcher)
                .build()
        )
        infoCard.addItem(
            MaterialAboutActionItem.Builder().text("Versión").subText(BuildConfig.VERSION_NAME)
                .icon(R.drawable.ic_version).build()
        )
        infoCard.addItem(
            MaterialAboutActionItem.Builder().text("Diagnóstico").icon(R.drawable.ic_diagnostic)
                .setOnClickAction { openDiagnostic() }.build()
        )

        val forkCard = MaterialAboutCard.Builder().outline(isFlat).title("Fork")
        forkCard.addItem(
            ConvenienceBuilder.createWebsiteActionItem(
                requireContext(), getDrawable(requireContext(), R.drawable.ic_author),
                "Altem01", false, "https://github.com/Altem1/UkiList".toUri()
            )
        )
        forkCard.addItem(
            ConvenienceBuilder.createWebsiteActionItem(
                requireContext(), getDrawable(requireContext(), R.drawable.ic_github),
                "Código del fork", true, "https://github.com/Altem1/UkiList".toUri()
            )
        )

        val creditsCard = MaterialAboutCard.Builder().outline(isFlat).title("Créditos")
        creditsCard.addItem(
            MaterialAboutActionItem.Builder().text("UKIKU por Jordy Alexis Mendoza Caballero")
                .subText("Proyecto original del que deriva este fork")
                .icon(R.drawable.ic_github)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(requireContext(), "https://github.com/jordyamc/UKIKU".toUri()))
                .build()
        )
        creditsCard.addItem(
            MaterialAboutActionItem.Builder().text("Licencia MIT de UKIKU")
                .subText("Copyright (c) 2018 Jordy Alexis Mendoza Caballero")
                .icon(R.drawable.ic_changelog_get)
                .setOnClickAction { showLicense("UKIKU", "2018 Jordy Alexis Mendoza Caballero") }.build()
        )
        creditsCard.addItem(
            MaterialAboutActionItem.Builder().text("DoubleTapPlayerView por Viktor Krez")
                .subText("Copyright (c) 2019 Viktor Krez · MIT · incluido como módulo :dtpv")
                .icon(R.drawable.ic_changelog_get)
                .setOnClickAction { showLicense("DoubleTapPlayerView", "2019 Viktor Krez") }.build()
        )
        creditsCard.addItem(
            MaterialAboutActionItem.Builder().text("AniList")
                .subText("Datos y sincronización vía la API pública de AniList")
                .icon(R.drawable.ic_web)
                .setOnClickAction(ConvenienceBuilder.createWebsiteOnClickAction(requireContext(), "https://anilist.co".toUri()))
                .build()
        )

        val extraCard = MaterialAboutCard.Builder().outline(isFlat).title("Extras")
        extraCard.addItem(
            MaterialAboutActionItem.Builder().text("Easter egg").icon(R.drawable.ic_egg)
                .setOnClickAction { EAUnlockActivity.start(requireContext()) }.build()
        )

        return MaterialAboutList.Builder()
            .addCard(infoCard.build())
            .addCard(forkCard.build())
            .addCard(creditsCard.build())
            .addCard(extraCard.build())
            .build()
    }

    private fun showLicense(project: String, copyright: String) {
        MaterialDialog(requireContext()).safeShow {
            title(text = "$project · Licencia MIT")
            message(text = mitLicense(copyright))
            positiveButton(text = "Cerrar")
        }
    }

    private fun mitLicense(copyright: String) = """MIT License

Copyright (c) $copyright

Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE."""

    private fun openDiagnostic() {
        if (DesignUtils.isFlat)
            DiagnosticMaterial.open(requireContext())
        else
            Diagnostic.open(requireContext())
    }
}
