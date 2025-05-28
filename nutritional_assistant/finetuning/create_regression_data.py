import json

input_path = "processed_data/roberta_training_data.json"
output_path = "processed_data/roberta_regression_data.json"

with open(input_path, "r", encoding="utf-8") as infile:
    data = json.load(infile)

regression_data = []
for entry in data:
    text = entry.get("text", "")
    glycemic_load = entry.get("label", {}).get("glycemic_load", None)
    if text and glycemic_load is not None:
        regression_data.append({"text": text, "label": float(glycemic_load)})

with open(output_path, "w", encoding="utf-8") as outfile:
    json.dump(regression_data, outfile, indent=2, ensure_ascii=False)

print(f"Wrote {len(regression_data)} regression examples to {output_path}") 