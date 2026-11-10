// Lumen installer: asks which launcher + which profile, then puts Lumen (+ Fabric API, optional Distant Horizons)
// straight into that profile's mods folder.
package main

import (
	"bufio"
	"embed"
	"encoding/json"
	"fmt"
	"io/fs"
	"os"
	"os/exec"
	"path/filepath"
	"regexp"
	"runtime"
	"sort"
	"strconv"
	"strings"
)

const mc = "1.21.11"

// Everything is bundled inside the exe, so it never downloads anything.
//
//go:embed bundled/*.jar
var bundled embed.FS

type profile struct {
	Name   string
	Mods   string
	Fabric bool
}

var in = bufio.NewReader(os.Stdin)

// Dawn (formerly Feather): look for "user-mods/<version>" folders and any "mods" folders under .dawn
func dawn() []profile {
	var out []profile
	seen := map[string]bool{}
	for _, root := range []string{filepath.Join(appdata(), ".dawn"), filepath.Join(appdata(), ".feather")} {
		if !exists(root) {
			continue
		}
		base := filepath.Base(root)
		filepath.WalkDir(root, func(path string, d fs.DirEntry, err error) error {
			if err != nil || !d.IsDir() {
				return nil
			}
			rel, _ := filepath.Rel(root, path)
			if strings.Count(rel, string(filepath.Separator)) > 5 {
				return filepath.SkipDir
			}
			low := strings.ToLower(d.Name())
			if low == "assets" || low == "libraries" || low == "versions" || low == "runtime" || low == "cache" || low == "logs" {
				return filepath.SkipDir
			}
			parent := strings.ToLower(filepath.Base(filepath.Dir(path)))
			var mods string
			if parent == "user-mods" {
				mods = path
			} else if low == "mods" {
				mods = path
			}
			if mods != "" && !seen[mods] {
				seen[mods] = true
				name := base + "\\" + rel
				lp := strings.ToLower(rel)
				fabric := strings.Contains(lp, mc) && (strings.Contains(lp, "fabric") || parent == "user-mods")
				out = append(out, profile{name, mods, fabric})
			}
			return nil
		})
	}
	return out
}

func appdata() string {
	if a := os.Getenv("APPDATA"); a != "" {
		return a
	}
	h, _ := os.UserHomeDir()
	if runtime.GOOS == "darwin" {
		return filepath.Join(h, "Library", "Application Support")
	}
	return h
}

func home() string { h, _ := os.UserHomeDir(); return h }

func mcDir() string {
	switch runtime.GOOS {
	case "windows":
		return filepath.Join(appdata(), ".minecraft")
	case "darwin":
		return filepath.Join(appdata(), "minecraft")
	}
	return filepath.Join(home(), ".minecraft")
}

func subdirs(base string) []string {
	var out []string
	es, err := os.ReadDir(base)
	if err != nil {
		return out
	}
	for _, e := range es {
		if e.IsDir() {
			out = append(out, filepath.Join(base, e.Name()))
		}
	}
	return out
}

func exists(p string) bool { _, err := os.Stat(p); return err == nil }

func official() []profile {
	var out []profile
	root := mcDir()
	b, err := os.ReadFile(filepath.Join(root, "launcher_profiles.json"))
	if err != nil {
		return out
	}
	var j struct {
		Profiles map[string]struct {
			Name          string `json:"name"`
			Type          string `json:"type"`
			LastVersionID string `json:"lastVersionId"`
			GameDir       string `json:"gameDir"`
		} `json:"profiles"`
	}
	if json.Unmarshal(b, &j) != nil {
		return out
	}
	for _, p := range j.Profiles {
		name := p.Name
		if name == "" {
			name = p.Type
		}
		if name == "" {
			name = "Unnamed profile"
		}
		fabric := strings.HasPrefix(p.LastVersionID, "fabric-loader") && strings.HasSuffix(p.LastVersionID, mc)
		if !fabric && p.LastVersionID != "" {
			name += "  (" + p.LastVersionID + ")"
		}
		dir := root
		if p.GameDir != "" {
			dir = p.GameDir
		}
		out = append(out, profile{name, filepath.Join(dir, "mods"), fabric})
	}
	return out
}

func prism() []profile {
	var out []profile
	bases := []string{
		filepath.Join(appdata(), "PrismLauncher", "instances"),
		filepath.Join(appdata(), "MultiMC", "instances"),
		filepath.Join(home(), ".local", "share", "PrismLauncher", "instances"),
	}
	for _, base := range bases {
		for _, d := range subdirs(base) {
			cfg, err := os.ReadFile(filepath.Join(d, "instance.cfg"))
			if err != nil {
				continue
			}
			name := filepath.Base(d)
			for _, line := range strings.Split(string(cfg), "\n") {
				if strings.HasPrefix(line, "name=") {
					name = strings.TrimSpace(line[5:])
					break
				}
			}
			game := filepath.Join(d, "minecraft")
			if !exists(game) && exists(filepath.Join(d, ".minecraft")) {
				game = filepath.Join(d, ".minecraft")
			}
			pack, _ := os.ReadFile(filepath.Join(d, "mmc-pack.json"))
			t := string(pack)
			fabric := strings.Contains(t, "net.fabricmc.fabric-loader") && strings.Contains(t, `"`+mc+`"`)
			out = append(out, profile{name, filepath.Join(game, "mods"), fabric})
		}
	}
	return out
}

func modrinth() []profile {
	var out []profile
	for _, base := range []string{filepath.Join(appdata(), "ModrinthApp", "profiles"), filepath.Join(appdata(), "com.modrinth.theseus", "profiles")} {
		for _, d := range subdirs(base) {
			n := filepath.Base(d)
			fabric := strings.Contains(strings.ToLower(n), "fabric") && strings.Contains(n, mc)
			out = append(out, profile{n, filepath.Join(d, "mods"), fabric})
		}
	}
	return out
}

func curseforge() []profile {
	var out []profile
	for _, d := range subdirs(filepath.Join(home(), "curseforge", "minecraft", "Instances")) {
		fabric := false
		if b, err := os.ReadFile(filepath.Join(d, "minecraftinstance.json")); err == nil {
			var j struct {
				GameVersion   string `json:"gameVersion"`
				BaseModLoader struct {
					Name string `json:"name"`
				} `json:"baseModLoader"`
			}
			if json.Unmarshal(b, &j) == nil {
				fabric = strings.HasPrefix(strings.ToLower(j.BaseModLoader.Name), "fabric") && j.GameVersion == mc
			}
		}
		out = append(out, profile{filepath.Base(d), filepath.Join(d, "mods"), fabric})
	}
	return out
}

// copy a bundled jar whose name starts with prefix into the mods folder; returns the file name
func install(prefix, mods, as string) (string, error) {
	es, _ := fs.ReadDir(bundled, "bundled")
	for _, e := range es {
		if strings.HasPrefix(strings.ToLower(e.Name()), prefix) {
			b, err := bundled.ReadFile("bundled/" + e.Name())
			if err != nil {
				return "", err
			}
			name := e.Name()
			if as != "" {
				name = as
			}
			return name, os.WriteFile(filepath.Join(mods, name), b, 0o644)
		}
	}
	return "", fmt.Errorf("%s is missing from the installer", prefix)
}

func has(mods string, re *regexp.Regexp) bool {
	es, _ := os.ReadDir(mods)
	for _, e := range es {
		if re.MatchString(strings.ToLower(e.Name())) {
			return true
		}
	}
	return false
}

func ask(prompt string) string {
	fmt.Print(prompt)
	s, _ := in.ReadString('\n')
	return strings.TrimSpace(s)
}

func pick(prompt string, max int) int {
	for {
		n, err := strconv.Atoi(ask(prompt))
		if err == nil && n >= 1 && n <= max {
			return n
		}
		fmt.Printf("  Type a number from 1 to %d.\n", max)
	}
}

func yes(prompt string) bool {
	a := strings.ToLower(ask(prompt + " [Y/n]: "))
	return a == "" || a == "y" || a == "yes"
}

func finish(code int) {
	ask("\nPress Enter to close...")
	os.Exit(code)
}

func main() {
	fmt.Println("==============================================")
	fmt.Println("   Lumen Installer  (Minecraft " + mc + ", Fabric)")
	fmt.Println("==============================================")
	fmt.Println()
	fmt.Println("Which launcher do you use?")
	fmt.Println("  1) Minecraft Launcher (official)")
	fmt.Println("  2) Prism Launcher / MultiMC")
	fmt.Println("  3) Modrinth App")
	fmt.Println("  4) CurseForge")
	fmt.Println("  5) Dawn (formerly Feather)")
	var found []profile
	switch pick("Type 1-5 and press Enter: ", 5) {
	case 1:
		found = official()
	case 2:
		found = prism()
	case 3:
		found = modrinth()
	case 4:
		found = curseforge()
	case 5:
		found = dawn()
	}
	if len(found) == 0 {
		fmt.Println("\nNo profiles found for that launcher.")
		fmt.Println("Make a Fabric " + mc + " profile/instance first, then run this again.")
		finish(1)
	}
	sort.SliceStable(found, func(i, j int) bool {
		if found[i].Fabric != found[j].Fabric {
			return found[i].Fabric
		}
		return strings.ToLower(found[i].Name) < strings.ToLower(found[j].Name)
	})
	fmt.Println("\nWhich profile? (Fabric " + mc + " ones are marked)")
	for i, p := range found {
		tag := ""
		if p.Fabric {
			tag = "   <- Fabric " + mc
		}
		fmt.Printf("  %d) %s%s\n", i+1, p.Name, tag)
	}
	p := found[pick(fmt.Sprintf("Type 1-%d and press Enter: ", len(found)), len(found))-1]
	if !p.Fabric {
		fmt.Println("\n'" + p.Name + "' doesn't look like a Fabric " + mc + " profile. Lumen only runs on Fabric " + mc + ".")
		if !yes("Install into it anyway?") {
			finish(0)
		}
	}
	fmt.Println()
	wantDH := yes("Also install Distant Horizons? (recommended, see much further)")

	fmt.Println()
	if err := os.MkdirAll(p.Mods, 0o755); err != nil {
		fmt.Println("Couldn't make the mods folder:", err)
		finish(1)
	}
	old := regexp.MustCompile(`^lumen.*\.jar$`)
	es, _ := os.ReadDir(p.Mods)
	for _, e := range es {
		if old.MatchString(strings.ToLower(e.Name())) {
			os.Remove(filepath.Join(p.Mods, e.Name()))
		}
	}
	fmt.Println("Installing Lumen...")
	if _, err := install("lumen", p.Mods, "lumen.jar"); err != nil {
		fmt.Println("  Failed:", err)
		finish(1)
	}
	if !has(p.Mods, regexp.MustCompile(`^fabric-api.*\.jar$`)) {
		fmt.Println("Installing Fabric API...")
		if _, err := install("fabric-api", p.Mods, ""); err != nil {
			fmt.Println("  Failed:", err)
		}
	} else {
		fmt.Println("Fabric API already there, skipping.")
	}
	if wantDH {
		if !has(p.Mods, regexp.MustCompile(`distant.?horizons.*\.jar$`)) {
			fmt.Println("Installing Distant Horizons...")
			if _, err := install("distanthorizons", p.Mods, ""); err != nil {
				fmt.Println("  Failed:", err)
			}
		} else {
			fmt.Println("Distant Horizons already there, skipping.")
		}
	}
	fmt.Println("\nDone! Lumen is installed in '" + p.Name + "'.")
	fmt.Println("Start Minecraft with that profile. Press F6 in game for Lumen settings.")
	if runtime.GOOS == "windows" {
		exec.Command("explorer.exe", p.Mods).Start()
	}
	finish(0)
}
