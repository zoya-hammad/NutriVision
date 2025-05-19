import pandas as pd
import matplotlib.pyplot as plt
import os
from datetime import datetime

# Create test results directory if it doesn't exist
os.makedirs('performance_data', exist_ok=True)

# Test results data
data = {
    'Agent': ['DeepSeek Agent', 'RoBERTa', 'GPT-4o Mini'],
    'Time (seconds)': [846.9, 11.3, 10.2],  # Converting 14m 6.9s to seconds
}

# Create DataFrame
df = pd.DataFrame(data)

# Save to CSV
csv_path = f'performance_data/deepseek_roberta_gpt4o_mini_performance_data.csv'
df.to_csv(csv_path, index=False)

# Create histogram
plt.figure(figsize=(10, 6))
plt.bar(df['Agent'], df['Time (seconds)'])
plt.title('Agent Performance Comparison')
plt.xlabel('Agent')
plt.ylabel('Time (seconds)')
plt.xticks(rotation=45)
plt.tight_layout()

# Save plot
plot_path = f'performance_data/deepseek_roberta_gpt4o_mini_performance_histogram.png'
plt.savefig(plot_path)
plt.close()

print(f"CSV file saved to: {csv_path}")
print(f"Histogram saved to: {plot_path}") 