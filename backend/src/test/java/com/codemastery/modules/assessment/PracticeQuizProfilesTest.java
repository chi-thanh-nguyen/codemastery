package com.codemastery.modules.assessment;

import com.codemastery.modules.assessment.application.service.QuestionSelectionService;
import com.codemastery.modules.assessment.application.service.QuizService;
import com.codemastery.modules.assessment.application.service.QuizService.PracticeQuizProfile;
import com.codemastery.modules.assessment.domain.model.DifficultyLevel;
import com.codemastery.modules.assessment.domain.repository.QuizRepository;
import com.codemastery.modules.assessment.domain.repository.QuizRepository.PracticeQuizDifficultyRow;
import com.codemastery.modules.course.application.service.CourseAccessService;
import com.codemastery.modules.course.application.service.CourseManagementService;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PracticeQuizProfilesTest {

    @Mock
    private QuizRepository quizzes;

    @Mock
    private QuestionSelectionService selection;

    @Mock
    private CourseAccessService courseAccess;

    @Mock
    private CourseManagementService courseConfiguration;

    @InjectMocks
    private QuizService quizService;

    @Test
    void emptySkillIdsReturnsEmptyListWithoutQueryingRepository() {
        UUID courseId = UUID.randomUUID();

        List<PracticeQuizProfile> result = quizService.readPracticeQuizProfiles(courseId, Set.of());

        assertTrue(result.isEmpty());
        verifyNoInteractions(quizzes);
    }

    @Test
    void aggregatesMinimalDifficultyRowsInOneQueryAndReturnsImmutableProfiles() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();
        UUID easyQuizId = UUID.randomUUID();
        UUID mixedQuizId = UUID.randomUUID();

        when(quizzes.findPublishedPracticeQuizDifficultyRows(courseId, Set.of(skillId)))
                .thenReturn(List.of(
                        new PracticeQuizDifficultyRow(easyQuizId, "Loops Easy", "easy"),
                        new PracticeQuizDifficultyRow(mixedQuizId, "Loops Mixed", "easy"),
                        new PracticeQuizDifficultyRow(mixedQuizId, "Loops Mixed", "hard")
                ));

        List<PracticeQuizProfile> profiles = quizService.readPracticeQuizProfiles(courseId, Set.of(skillId));

        verify(quizzes, times(1)).findPublishedPracticeQuizDifficultyRows(courseId, Set.of(skillId));
        assertEquals(2, profiles.size());
        assertEquals(new PracticeQuizProfile(easyQuizId, "Loops Easy", Set.of(DifficultyLevel.EASY)), profiles.get(0));
        assertEquals(
                new PracticeQuizProfile(mixedQuizId, "Loops Mixed", Set.of(DifficultyLevel.EASY, DifficultyLevel.HARD)),
                profiles.get(1)
        );
        assertThrows(UnsupportedOperationException.class, () -> profiles.add(profiles.get(0)));
        assertThrows(UnsupportedOperationException.class, () -> profiles.get(0).difficulties().add(DifficultyLevel.HARD));
    }

    @Test
    void rejectsNullArgumentsAndInvalidStoredDifficulty() {
        UUID courseId = UUID.randomUUID();
        UUID skillId = UUID.randomUUID();

        assertThrows(IllegalArgumentException.class, () -> quizService.readPracticeQuizProfiles(null, Set.of(skillId)));
        assertThrows(NullPointerException.class, () -> quizService.readPracticeQuizProfiles(courseId, null));

        Set<UUID> withNull = new HashSet<>();
        withNull.add(null);
        assertThrows(IllegalArgumentException.class, () -> quizService.readPracticeQuizProfiles(courseId, withNull));

        when(quizzes.findPublishedPracticeQuizDifficultyRows(courseId, Set.of(skillId)))
                .thenReturn(List.of(new PracticeQuizDifficultyRow(UUID.randomUUID(), "Bad Quiz", "extreme")));
        assertThrows(IllegalStateException.class, () -> quizService.readPracticeQuizProfiles(courseId, Set.of(skillId)));
    }
}