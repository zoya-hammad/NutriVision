# NutriVision: Nutritional Assistant Project Documentation

## Project Overview
NutriVision is an advanced nutritional analysis system that combines multiple AI models and agents to provide comprehensive recipe analysis and nutritional guidance.

## System Architecture

### High-Level System Flow
```mermaid
flowchart TD
    A[Data Collection & Preprocessing] --> B[Vector Database Creation]
    B --> C[Model Development & Testing]
    C --> D[Agent System]
    D --> E[Evaluation System]

    subgraph "Data Pipeline"
        A1[Recipe Dataset] --> A2[Data Cleaning]
        A2 --> A3[Visualization]
        A3 --> A4[Vector Embeddings]
    end

    subgraph "Model Pipeline"
        C1[Model Testing] --> C2[Model Selection]
        C2 --> C3[Fine-tuning]
        C3 --> C4[Deployment]
    end

    subgraph "Agent System"
        D1[Recipe Agent] --> D2[GI Analysis Agent]
        D2 --> D3[Grader Agent]
    end
```

### Model Selection & Deployment Pipeline
```mermaid
flowchart TD
    subgraph "Initial Model Testing"
        A[Model Candidates] --> B[Performance Evaluation]
        
        subgraph "Deployment Methods"
            B1[AWS Hugging Face Endpoints] --> B2[RoBERTa]
            B1 --> B3[DeepSeek]
            B4[OpenAI API] --> B5[GPT-3.5/4]
        end
        
        B2 & B3 & B5 --> C[Model Comparison]
        C --> D[Selection Criteria]
        D --> E[RoBERTa Selected]
    end

    subgraph "Selected Model Pipeline"
        E --> F[Fine-tuning]
        F --> G[Weights & Biases]
        G --> H[Hugging Face Deployment]
    end
```

### GI Analysis Agent Pipeline
```mermaid
flowchart TD
    subgraph "GI Analysis Process"
        A[Recipe Input] --> B[Ingredient Extraction]
        B --> C[Multiple API Calls]
        
        subgraph "API Integration"
            C1[Food Database API] --> C2[GI Values]
            C3[Nutrition API] --> C4[Carb Content]
            C5[Unit Conversion API] --> C6[Standardized Units]
        end
        
        C2 & C4 & C6 --> D[GI Calculation]
        D --> E[Load Calculation]
        E --> F[Impact Assessment]
        F --> G[Final GI Score]
    end

    subgraph "External Services"
        H[Food Database] --> C1
        I[Nutrition API] --> C3
        J[Unit Converter] --> C5
    end
```

### Recipe Agent Pipeline
```mermaid
flowchart TD
    subgraph "Recipe Processing"
        A[User Query] --> B[Query Processing]
        B --> C[Vector Search]
        
        subgraph "Multi-step Analysis"
            C --> D1[Ingredient Analysis]
            C --> D2[Recipe Similarity]
            C --> D3[Nutritional Profile]
            
            D1 & D2 & D3 --> E[Recipe Ranking]
        end
        
        E --> F[Recipe Selection]
        F --> G[Detailed Analysis]
    end

    subgraph "External Services"
        H[Chroma DB] --> C
        I[LLM Service] --> D1
        J[Nutrition API] --> D3
    end
```

### Agent System Architecture
```mermaid
flowchart TD
    subgraph "Agent System"
        A[Recipe Agent] --> B[Vector DB Query]
        B --> C[Recipe Analysis]
        
        C --> D[GI Analysis Agent]
        D --> E[GI Calculation]
        E --> F[Nutritional Impact]
        
        F --> G[Grader Agent]
        G --> H[Ensemble Grading]
        H --> I[Quality Validation]
    end

    subgraph "External Services"
        J[Hugging Face Model] --> D
        K[Chroma DB] --> B
        L[Ollama Local] --> F
    end
```

## Detailed Workflow

### 1. Data Preprocessing Pipeline
- **Recipe Dataset Processing**
  - Collection and cleaning of recipe data
  - Standardization of formats
  - Data validation and quality checks
  - Creation of visualizations for data analysis

- **Vector Database Creation**
  - Generation of vector embeddings using LLM
  - Creation of Chroma database for efficient storage
  - Implementation of similarity search capabilities

### 2. Model Development & Testing
- **Initial Model Testing**
  - Deployment of multiple models:
    - RoBERTa (AWS Hugging Face Inference Endpoint)
    - DeepSeek (AWS Hugging Face Inference Endpoint)
    - GPT-3.5/4 (OpenAI API)
  - Performance comparison and evaluation
  - Selection of RoBERTa as primary model based on:
    - Accuracy in GI prediction
    - Response time
    - Cost efficiency
    - API reliability

- **Model Fine-tuning**
  - GPU-based fine-tuning on Google Colab
  - Integration with Weights & Biases for:
    - Training metrics visualization
    - Resource utilization tracking
    - Model performance monitoring
    - Hyperparameter optimization
  - Deployment to Hugging Face:
    - Model versioning
    - Inference endpoint creation
    - API integration
  - Performance validation

### 3. Agent System Implementation
- **Recipe Agent**
  - Multi-step recipe processing:
    - Query understanding and processing
    - Vector similarity search
    - Ingredient analysis
    - Recipe similarity matching
    - Nutritional profile generation
    - Recipe ranking and selection
  - Integration with Chroma DB for efficient retrieval
  - LLM-powered ingredient analysis

- **GI Analysis Agent**
  - Complex pipeline for GI calculation:
    - Ingredient extraction and normalization
    - Multiple API integrations:
      - Food database for GI values
      - Nutrition API for carb content
      - Unit conversion service
    - GI and load calculations
    - Nutritional impact assessment
  - Real-time processing capabilities

- **Grader Agent System**
  - Implementation of ensemble grading
  - Integration of two Claude models
  - Quality assurance and validation

### 4. Testing & Evaluation
- **Comprehensive Testing**
  - Test categories:
    - Simple recipes
    - High GI recipes
    - Low GI recipes
  - Performance metrics collection
  - Error analysis and improvement

- **Evaluation Framework**
  - Ensemble grader agent implementation
  - Cross-validation with expert assessments
  - Performance benchmarking

## Technical Components

### Data Processing
- Vector embeddings generation
- Chroma database implementation
- Local Ollama integration for nutritional guidelines

### Model Architecture
- RoBERTa fine-tuning pipeline
  - Weights & Biases integration for monitoring
  - Training visualization and metrics tracking
  - Model checkpoint management
- Hugging Face deployment
  - Model versioning and registry
  - Inference endpoint configuration
  - API integration
- Model evaluation framework

### Agent System
- Recipe querying system
  - Multi-step processing pipeline
  - Vector similarity search
  - LLM integration
- GI analysis pipeline
  - Multiple API integrations
  - Real-time calculations
  - Unit conversion handling
- Grading mechanism

## Performance Analysis
- Model accuracy metrics
- Processing time optimization
- Resource utilization
- Weights & Biases dashboard metrics:
  - Training loss curves
  - Validation metrics
  - Resource consumption
  - Model performance comparisons

## Future Enhancements
- Additional model integrations
- Extended recipe database
- Enhanced nutritional analysis capabilities
- Advanced monitoring and visualization features