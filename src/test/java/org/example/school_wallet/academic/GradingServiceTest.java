package org.example.school_wallet.academic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class GradingServiceTest {

    private final AppSettingRepository settings = mock(AppSettingRepository.class);
    private final GradingService service = new GradingService(settings);

    @Test
    void usesDefaultBandsWhenSettingIsMissing() {
        when(settings.findBySettingKey("grading.bands")).thenReturn(Optional.empty());

        assertEquals("A+", service.grade(92.0, 100.0));
        assertEquals("A", service.grade(85.0, 100.0));
        assertEquals("B", service.grade(72.0, 100.0));
        assertEquals("F", service.grade(35.0, 100.0));
        assertEquals("", service.grade(null, 100.0));
    }

    @Test
    void usesConfiguredBands() {
        AppSetting setting = new AppSetting();
        setting.setSettingValue("75:Distinction,50:Pass,0:Fail");
        when(settings.findBySettingKey("grading.bands")).thenReturn(Optional.of(setting));

        assertEquals("Distinction", service.grade(80.0, 100.0));
        assertEquals("Pass", service.grade(60.0, 100.0));
        assertEquals("Fail", service.grade(20.0, 100.0));
    }
}
