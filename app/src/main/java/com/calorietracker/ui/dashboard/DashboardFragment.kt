package dev.kkrow.calorietracker.ui.dashboard

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import dev.kkrow.calorietracker.R
import dev.kkrow.calorietracker.data.AppLanguageManager
import dev.kkrow.calorietracker.data.CalorieRepository
import dev.kkrow.calorietracker.databinding.FragmentDashboardBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class DashboardFragment : Fragment() {

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private lateinit var repository: CalorieRepository
    private var languageOptions: List<AppLanguageManager.LanguageOption> = emptyList()

    private val exportLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val ok = try {
                requireContext().contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(repository.exportToJson().toByteArray(Charsets.UTF_8))
                }
                true
            } catch (_: Exception) {
                false
            }

            val msg = if (ok) R.string.export_success else R.string.export_error
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
        }
    }

    private val importLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = result.data?.data ?: return@registerForActivityResult
            val json = try {
                requireContext().contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            } catch (_: Exception) {
                null
            }

            val ok = json?.let { repository.importFromJson(it) } == true
            val msg = if (ok) R.string.import_success else R.string.import_error
            Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show()
            if (ok) {
                loadLimit()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        repository = CalorieRepository(requireContext())

        setupLanguageSelector()
        loadLimit()

        binding.buttonSaveLimit.setOnClickListener {
            saveLimit()
        }

        binding.buttonExport.setOnClickListener {
            exportData()
        }

        binding.buttonImport.setOnClickListener {
            importData()
        }

        return binding.root
    }

    private fun setupLanguageSelector() {
        languageOptions = AppLanguageManager.getSupportedLanguages()
        val labels = languageOptions.map { it.label }
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            labels
        )
        binding.inputLanguage.setAdapter(adapter)
        binding.inputLanguage.threshold = Int.MAX_VALUE
        binding.inputLanguage.setOnClickListener { binding.inputLanguage.showDropDown() }

        val current = AppLanguageManager.getCurrentLanguageTag(requireContext())
        val currentOption = languageOptions.firstOrNull { it.tag.equals(current, ignoreCase = true) }
            ?: languageOptions.first()
        binding.inputLanguage.setText(currentOption.label, false)

        binding.inputLanguage.setOnItemClickListener { _, _, position, _ ->
            val option = languageOptions[position]
            val currentTag = AppLanguageManager.getCurrentLanguageTag(requireContext())
            if (!option.tag.equals(currentTag, ignoreCase = true)) {
                AppLanguageManager.setLanguage(requireContext(), option.tag)
            }
        }
    }

    private fun loadLimit() {
        binding.inputDailyLimit.setText(repository.getDailyLimit().toString())
    }

    private fun saveLimit() {
        val value = binding.inputDailyLimit.text?.toString()?.trim()?.toIntOrNull()
        if (value == null || value <= 0) {
            binding.inputDailyLimit.error = getString(R.string.invalid_number)
            return
        }

        repository.setDailyLimit(value)
        Toast.makeText(requireContext(), R.string.saved, Toast.LENGTH_SHORT).show()
    }

    private fun exportData() {
        val fileName = "calorie_history_${SimpleDateFormat("yyyyMMdd", Locale.getDefault()).format(Date())}.json"
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, fileName)
        }
        exportLauncher.launch(intent)
    }

    private fun importData() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
        }
        importLauncher.launch(intent)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
