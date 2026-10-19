package com.javadsamastery.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class TopicMasterySnapshotId implements Serializable {

    @Column(name = "learner_id", length = 64, nullable = false)
    private String learnerId;

    @Column(name = "topic_id", length = 64, nullable = false)
    private String topicId;

    public TopicMasterySnapshotId() {}

    public TopicMasterySnapshotId(String learnerId, String topicId) {
        this.learnerId = learnerId;
        this.topicId = topicId;
    }

    public String getLearnerId() { return learnerId; }
    public void setLearnerId(String learnerId) { this.learnerId = learnerId; }

    public String getTopicId() { return topicId; }
    public void setTopicId(String topicId) { this.topicId = topicId; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TopicMasterySnapshotId that = (TopicMasterySnapshotId) o;
        return Objects.equals(learnerId, that.learnerId) && Objects.equals(topicId, that.topicId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(learnerId, topicId);
    }
}
