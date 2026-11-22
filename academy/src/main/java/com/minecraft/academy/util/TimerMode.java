package com.minecraft.academy.util;

import com.minecraft.core.account.context.objects.academy.AcademyMetadata;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.function.BiConsumer;
import java.util.function.ToLongFunction;

@Getter
@AllArgsConstructor
public enum TimerMode {

    REFILL("REFILL", "refill", "refill de sopas", AcademyMetadata::getTimerRefill, AcademyMetadata::setTimerRefill),
    MUSHROOM("COGUMELO", "recraft", "recraft de Cogumelo", AcademyMetadata::getTimerMushroom, AcademyMetadata::setTimerMushroom),
    COCOA("COCOA", "recraft", "recraft de Cocoa", AcademyMetadata::getTimerCocoa, AcademyMetadata::setTimerCocoa);

    private final String identifier, kind, description;

    private final ToLongFunction<AcademyMetadata> recordGetter;
    private final BiConsumer<AcademyMetadata, Long> recordSetter;

    public long getRecord(AcademyMetadata academy) {
        return recordGetter.applyAsLong(academy);
    }

    public void setRecord(AcademyMetadata academy, long millis) {
        recordSetter.accept(academy, millis);
    }
}
