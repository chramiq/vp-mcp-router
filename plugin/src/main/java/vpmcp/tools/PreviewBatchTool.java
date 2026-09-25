package vpmcp.tools;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.vp.plugin.model.IProject;
import vpmcp.core.McpTool;
import vpmcp.core.McpToolException;
import vpmcp.core.ToolDefinition;
import vpmcp.vp.DiagramLocator;
import vpmcp.write.PlanValidator;
import vpmcp.write.TypeTiers;

/**
 * Dry-runs a write batch: validates every op and returns the resolved plan
 * without creating, updating, or saving anything. Nothing here mutates.
 */
public final class PreviewBatchTool implements McpTool {

    private static final String DESCRIPTION =
            "Validate a batch of diagram writes without applying anything. "
            + "Type gates are tiered: verified families behave exactly as documented; "
            + "any other type from the schema pack (see the vp://schemas resources: "
            + "diagram-types, factory-creates) is accepted but flagged \"unverified\" in "
            + "the plan — VP may veto silently or misplace, so read the result back; "
            + "types outside the pack are impossible and rejected. "
            + "Every op carries its discriminator, e.g. {\"op\": \"create_element\", ...}. "
            + "Ops: create_diagram {id, diagram_type, name}, "
            + "create_element {id, diagram, model_type, name, x, y, width, height}, "
            + "connect {id, diagram, rel_type, from, to, name, points?}, "
            + "add_member {id, parent, member_type, name, type?, visibility?, multiplicity?, "
            + "initial_value?, return_type?, parameters?, length?, nullable?} "
            + "(parent is a view id — view_id from an applied entry — or a plan ref; "
            + "Attribute takes type/visibility/multiplicity/initial_value, Operation "
            + "return_type/visibility/parameters (full replacement), DBColumn "
            + "type/length/nullable; inapplicable fields are skipped with a note), "
            + "update_element {id, model, name?, documentation?, stereotypes?} "
            + "(stereotypes replace all; empty documentation clears it), "
            + "update_member {id, member, name?, type?, visibility?, multiplicity?, "
            + "initial_value?, return_type?, parameters?, length?, nullable?} "
            + "(Attribute takes type/visibility/multiplicity/initial_value, Operation "
            + "return_type/visibility/parameters (full replacement), DBColumn "
            + "type/length/nullable; inapplicable fields are skipped with a note), "
            + "style_element {id, element, background?, fill_color?, foreground?, line_color?, "
            + "line_weight?, font_color?, font_size?, font_bold?, font_italic?, "
            + "font_name?} (hex colours like \"#FF0000\", matching what "
            + "get_diagram_by_url reports; fill_color is the shape fill, "
            + "background is the element background), "
            + "create_raw {id, factory_method, diagram?, name?, x?, y?, width?, height?} "
            + "(escape hatch: any no-arg create method of IModelElementFactory, for example "
            + "\"createRequirement\"; unverified family — the result may refuse placement "
            + "on the given diagram, and a few kinds are transient in VP, never saved or "
            + "listed; see the vp://schemas factory-creates resource for the method set), "
            + "duplicate_diagram {id, diagram, name} (shallow copy: fresh views, shared models), "
            + "move_element {id, element, x?, y?, width?, height?}, "
            + "show_element {id, diagram, model, x?, y?, width?, height?} (view of an existing model), "
            + "delete_model {id, model} (model plus all its views, final), "
            + "delete_diagram {id, diagram}, delete_element {id, element}. "
            + "Ids and diagram/endpoint slots accept plan refs as {\"ref\": \"<op id>\"}. "
            + "Applied entries report vp_id as the model id (stable identity) plus "
            + "view_id where a view exists. "
            + "Returns {valid, plan[] (each with its undo), errors[]}; valid plans are applied with apply_batch.";

    private static final String INPUT_SCHEMA =
            "{\"type\":\"object\","
            + "\"properties\":{\"ops\":{\"type\":\"array\",\"description\":\"Write ops in dependency order.\"}},"
            + "\"required\":[\"ops\"],\"additionalProperties\":false}";

    private final ToolDefinition definition = new ToolDefinition("vp_preview_batch", DESCRIPTION,
            JsonParser.parseString(INPUT_SCHEMA).getAsJsonObject());
    private final TypeTiers tiers;

    /** Verified-only tiers: the pre-pack behavior, for dev servers and tests. */
    public PreviewBatchTool() {
        this(TypeTiers.verifiedOnly());
    }

    public PreviewBatchTool(TypeTiers tiers) {
        this.tiers = tiers;
    }

    @Override
    public ToolDefinition getDefinition() {
        return definition;
    }

    @Override
    public JsonObject execute(JsonObject params) throws McpToolException {
        JsonElement ops = params.get("ops");
        if (ops == null || !ops.isJsonArray()) {
            throw new McpToolException("\"ops\" is required and must be an array.");
        }
        IProject project = DiagramLocator.requireOpenProject();
        JsonObject result = new JsonObject();
        result.addProperty("mutated", false);
        result.add("validation", PlanValidator.validate(project, ops.getAsJsonArray(), tiers));
        return result;
    }
}
