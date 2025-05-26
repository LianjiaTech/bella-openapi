# Bella OpenAPI Contributor Guide

English | [中文](./contributor-guide.md)

Welcome to the Bella OpenAPI project! This guide aims to help you get started and contribute to the project. We greatly appreciate your interest and contributions.

## Table of Contents

- [Project Overview](#project-overview)
- [Getting Started](#getting-started)
- [Development Process](#development-process)
- [Submitting Issues](#submitting-issues)
- [Code Contributions](#code-contributions)
- [Code Style](#code-style)
- [Testing](#testing)
- [FAQ](#faq)
- [Community Support](#community-support)
- [Contributors](#contributors)

## Project Overview

Bella OpenAPI is an open-source API management platform focused on API design, development, and management. The project aims to provide an efficient and user-friendly API ecosystem to help developers build and maintain APIs more easily.

## Getting Started

Before contributing code, please ensure you have completed the following steps:

1. **Understand the Project**:
   - Read the [Technical Documentation](https://doc.bella.top/docs/bella-openapi/tech/metadata) to understand core concepts and technical details
   - Use [DeepWiki](https://deepwiki.com/LianjiaTech/bella-openapi) to analyze project structure and codebase
   - Check the [Startup and Deployment Guide](https://doc.bella.top/docs/bella-openapi/startup-deployment-details) to learn how to set up your local development environment

2. **Environment Setup**:
   - Ensure you have installed necessary development tools and dependencies
   - Successfully run the project locally following the startup guide (while only Docker startup is documented, developers can set up the project manually based on configuration details)

## Development Process

1. **Fork the Repository**: Fork the [Bella OpenAPI repository](https://github.com/LianjiaTech/bella-openapi) on GitHub

2. **Clone the Repository**:
   ```bash
   git clone https://github.com/YOUR-USERNAME/bella-openapi.git
   cd bella-openapi
   ```

3. **Install Dependencies**:
   Frontend:
   ```bash
   cd web
   npm install
   ```
   Backend:
   ```bash
   cd api
   mvn install -DskipTests
   ```

4. **Create Branch**:
   ```bash
   git checkout -b feature/your-feature-name
   ```

5. **Development**: Make code changes and feature development, including debugging

6. **Testing**: Ensure your code passes all tests

7. **Commit Changes**:
   ```bash
   git commit -m "describe your changes"
   ```

8. **Push to GitHub**:
   ```bash
   git push origin feature/your-feature-name
   ```

9. **Create Pull Request**: Create a PR to the original repository on GitHub

## Submitting Issues

We use GitHub Issues to track problems and feature requests. When submitting an Issue:

1. Use a clear title to describe the problem or requirement
2. Provide detailed description, including how to reproduce the issue or specific feature requirements
3. Attach screenshots or relevant code if possible
4. Set appropriate labels for the Issue

**Special Note**: The Bella OpenAPI project is equipped with an intelligent `bella-issues-bot` that automatically processes submitted Issues:
- For problem-related Issues: the bot will automatically answer
- For feature requests: the bot will analyze requirements and may automatically implement code, then push to corresponding branches

You can learn more about this automation tool in the [bella-issues-bot repository](https://github.com/bella-top/bella-issues-bot).

## Code Contributions

### Pull Request Process

1. Ensure your PR contains only one clear change or feature
2. Update relevant documentation
3. Add necessary tests
4. Ensure all tests pass
5. Provide detailed description when submitting PR

### Code Review

All submitted PRs will undergo code review. Please respond promptly to reviewers' feedback and suggestions.

## Code Style

Please follow the project's existing code style and best practices:

1. Use consistent naming conventions
2. Write clear comments
3. Follow modular design principles
4. Avoid overly complex code
5. Write testable code

## Testing

Before submitting code, please ensure:

1. Add appropriate unit tests
2. Ensure all tests pass
3. For UI changes, perform necessary visual and functional testing

## FAQ

### How to Handle Dependency Conflicts?

If you encounter dependency conflicts, try these steps:
1. Clear local dependency cache
2. Ensure using project-recommended dependency versions
3. Check version constraints in `package.json`

### How to Debug Common Issues?

1. Use browser developer tools for frontend issues
2. Check server logs for backend errors
3. Use breakpoints to debug code execution flow

### Development Environment Won't Start?

1. Confirm all necessary services are running
2. Check if configuration files are correct
3. Verify if ports are available
4. Check error logs for detailed information

## Community Support

We encourage active participation in community discussions and interactions:

1. GitHub Discussions for general discussions and questions
2. Issues for specific problem reports and feature requests
3. Pull Requests for code contributions

## Contributors

We sincerely thank all developers who have contributed to the Bella OpenAPI project!

<table>
  <tr>
    <td align="center">
      <a href="https://github.com/szl97">
        <img src="https://github.com/szl97.png" width="100px;" alt="szl97"/>
        <br />
        <sub><b>szl97</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/nil4u">
        <img src="https://github.com/nil4u.png" width="100px;" alt="nil4u"/>
        <br />
        <sub><b>nil</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/Jiakaic">
        <img src="https://github.com/Jiakaic.png" width="100px;" alt="Jiakaic"/>
        <br />
        <sub><b>Jiakaic</b></sub>
      </a>
    </td>
    <td align="center">
      <a href="https://github.com/chlsmile">
        <img src="https://github.com/chlsmile.png" width="100px;" alt="chlsmile"/>
        <br />
        <sub><b>chlsmile</b></sub>
      </a>
    </td>
     <td align="center">
      <a href="https://github.com/shenenqing">
        <img src="https://github.com/shenenqing.png" width="100px;" alt="shenenqing"/>
        <br />
        <sub><b>shenenqing</b></sub>
      </a>
    </td>
  </tr>
  <tr>
    <td align="center">
      <a href="https://github.com/Rheasilvia">
        <img src="https://github.com/Rheasilvia.png" width="100px;" alt="Rheasilvia"/>
        <br />
        <sub><b>Mengjie Chen</b></sub>
      </a>
    </td>
  </tr>
</table>

### How to Join the Contributors List

After your Pull Request is merged, project maintainers will add you to the contributors list. If you have contributed but are not listed, please remind us through an Issue or PR.

---

Thank you for contributing to Bella OpenAPI! If you have any questions, please feel free to contact project maintainers or raise an Issue on GitHub.
