package org.example.school_wallet.academic;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Computes grade bands from scores, configurable via the grading.bands setting. */
@Service
@Transactional(readOnly = true)
public class GradingService {

    private static final String DEFAULT_BANDS = "90:A+,80:A,70:B,60:C,50:D,40:E,0:F";

    private final AppSettingRepository settings;

    public GradingService(AppSettingRepository settings) {
        this.settings = settings;
    }

    public String grade(Double score, Double maxScore) {
        if (score == null || maxScore == null || maxScore <= 0) {
            return "";
        }
        double percentage = (score / maxScore) * 100;
        return bands().stream()
                .filter(band -> percentage >= band.minimum())
                .findFirst()
                .map(Band::label)
                .orElse("F");
    }

    public double averageScore(List<GradeRecord> records) {
        if (records.isEmpty()) {
            return 0;
        }
        return records.stream()
                .mapToDouble(record -> {
                    double max = record.getMaxScore() == null ? 100 : record.getMaxScore();
                    return max <= 0 ? 0 : (record.getScore() == null ? 0 : record.getScore() / max * 100);
                })
                .average()
                .orElse(0);
    }

    public Map<String, Object> summary(List<GradeRecord> records) {
        double average = averageScore(records);
        return Map.of(
                "count", records.size(),
                "average", Math.round(average * 10) / 10.0,
                "grade", grade(average, 100.0));
    }

    private List<Band> bands() {
        String raw = settings.findBySettingKey("grading.bands")
                .map(AppSetting::getSettingValue)
                .filter(value -> value != null && !value.isBlank())
                .orElse(DEFAULT_BANDS);

        List<Band> parsed = java.util.Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(part -> part.contains(":"))
                .map(part -> {
                    String[] pieces = part.split(":", 2);
                    return new Band(Double.parseDouble(pieces[0].trim()), pieces[1].trim());
                })
                .sorted(Comparator.comparingDouble(Band::minimum).reversed())
                .toList();
        return parsed.isEmpty() ? List.of(new Band(0, "F")) : parsed;
    }

    private record Band(double minimum, String label) {
    }
}
