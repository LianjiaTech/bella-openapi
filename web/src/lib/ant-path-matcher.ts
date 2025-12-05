//spring的AntPathMatcher
export class AntPathMatcher {
    private static pathSeparator: string = '/';
    private static caseSensitive: boolean = true;

    public static match(pattern: string, path: string): boolean {
        return this.doMatch(pattern, path, true, null);
    }

    public static doMatch(pattern: string, path: string, fullMatch: boolean, uriTemplateVariables: Map<string, string> | null = null): boolean {
        if (path.startsWith(this.pathSeparator) !== pattern.startsWith(this.pathSeparator)) {
            return false;
        }

        const pattDirs = this.tokenizePattern(pattern);
        if (fullMatch && this.caseSensitive && !this.isPotentialMatch(path, pattDirs)) {
            return false;
        }

        const pathDirs = this.tokenizePath(path);

        let pattIdxStart = 0;
        let pattIdxEnd = pattDirs.length - 1;
        let pathIdxStart = 0;
        let pathIdxEnd = pathDirs.length - 1;

        // Match all elements up to the first **
        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            const pattDir = pattDirs[pattIdxStart];
            if (pattDir === "**") {
                break;
            }
            if (!this.matchStrings(pattDir, pathDirs[pathIdxStart], uriTemplateVariables)) {
                return false;
            }
            pattIdxStart++;
            pathIdxStart++;
        }

        if (pathIdxStart > pathIdxEnd) {
            // Path is exhausted, only match if rest of pattern is * or **'s
            if (pattIdxStart > pattIdxEnd) {
                return (pattern.endsWith(this.pathSeparator) === path.endsWith(this.pathSeparator));
            }
            if (!fullMatch) {
                return true;
            }
            if (pattIdxStart === pattIdxEnd && pattDirs[pattIdxStart] === "*" && path.endsWith(this.pathSeparator)) {
                return true;
            }
            for (let i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (pattDirs[i] !== "**") {
                    return false;
                }
            }
            return true;
        }
        else if (pattIdxStart > pattIdxEnd) {
            // String not exhausted, but pattern is. Failure.
            return false;
        }
        else if (!fullMatch && pattDirs[pattIdxStart] === "**") {
            // Path start definitely matches due to "**" part in pattern.
            return true;
        }

        // up to last '**'
        while (pattIdxStart <= pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            const pattDir = pattDirs[pattIdxEnd];
            if (pattDir === "**") {
                break;
            }
            if (!this.matchStrings(pattDir, pathDirs[pathIdxEnd], uriTemplateVariables)) {
                return false;
            }
            pattIdxEnd--;
            pathIdxEnd--;
        }
        if (pathIdxStart > pathIdxEnd) {
            // String is exhausted
            for (let i = pattIdxStart; i <= pattIdxEnd; i++) {
                if (pattDirs[i] !== "**") {
                    return false;
                }
            }
            return true;
        }

        while (pattIdxStart !== pattIdxEnd && pathIdxStart <= pathIdxEnd) {
            let patIdxTmp = -1;
            for (let i = pattIdxStart + 1; i <= pattIdxEnd; i++) {
                if (pattDirs[i] === "**") {
                    patIdxTmp = i;
                    break;
                }
            }
            if (patIdxTmp === pattIdxStart + 1) {
                // '**/**' situation, so skip one
                pattIdxStart++;
                continue;
            }
            // Find the pattern between padIdxStart & padIdxTmp in str between
            // strIdxStart & strIdxEnd
            const patLength = (patIdxTmp - pattIdxStart - 1);
            const strLength = (pathIdxEnd - pathIdxStart + 1);
            let foundIdx = -1;

            strLoop:
                for (let i = 0; i <= strLength - patLength; i++) {
                    for (let j = 0; j < patLength; j++) {
                        const subPat = pattDirs[pattIdxStart + j + 1];
                        const subStr = pathDirs[pathIdxStart + i + j];
                        if (!this.matchStrings(subPat, subStr, uriTemplateVariables)) {
                            continue strLoop;
                        }
                    }
                    foundIdx = pathIdxStart + i;
                    break;
                }

            if (foundIdx === -1) {
                return false;
            }

            pattIdxStart = patIdxTmp;
            pathIdxStart = foundIdx + patLength;
        }

        for (let i = pattIdxStart; i <= pattIdxEnd; i++) {
            if (pattDirs[i] !== "**") {
                return false;
            }
        }

        return true;
    }

    private static tokenizePattern(pattern: string): string[] {
        // Implement pattern tokenization
        return pattern.split(this.pathSeparator);
    }

    private static tokenizePath(path: string): string[] {
        // Implement path tokenization
        return path.split(this.pathSeparator);
    }

    private static isPotentialMatch(path: string, pattDirs: string[]): boolean {
        // Implement potential match check
        return true; // Simplified for this example
    }

    private static matchStrings(pattern: string, str: string, uriTemplateVariables: Map<string, string> | null): boolean {
        // Implement string matching
        for(let i = 0; i < pattern.length; i++) {
            if(pattern.charAt(i) === '*') {
                continue;
            }
            if (pattern.charAt(i) !== str.charAt(i)) {
                return false;
            }
        }
        return true;
    }
}
