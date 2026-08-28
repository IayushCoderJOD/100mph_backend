package in.hundredmph.api.domain.billing;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

/** Mirrors Plan in src/data/types.ts. Price stays a display string on purpose. */
@Document(collection = "plans")
public class Plan {

    @Id
    private String id;

    private String name;

    private String description;

    @Field("duration_days")
    private int durationDays;

    @Field("price_label")
    private String priceLabel;

    public Plan() {}

    public Plan(String id, String name, String description, int durationDays, String priceLabel) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.durationDays = durationDays;
        this.priceLabel = priceLabel;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public int getDurationDays() { return durationDays; }
    public void setDurationDays(int durationDays) { this.durationDays = durationDays; }

    public String getPriceLabel() { return priceLabel; }
    public void setPriceLabel(String priceLabel) { this.priceLabel = priceLabel; }
}
