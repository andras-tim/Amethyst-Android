package net.kdt.pojavlaunch.fragments;

import static net.kdt.pojavlaunch.Tools.hasNoOnlineProfileDialog;
import static net.kdt.pojavlaunch.Tools.hasOnlineProfile;

import android.app.AlertDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import net.kdt.pojavlaunch.PojavProfile;
import net.kdt.pojavlaunch.R;
import net.kdt.pojavlaunch.Tools;
import net.kdt.pojavlaunch.links.LinkRouter;

public class ProfileTypeSelectFragment extends Fragment {
    public static final String TAG = "ProfileTypeSelectFragment";
    public ProfileTypeSelectFragment() {
        super(R.layout.fragment_profile_type);
    }
    public ProfileTypeSelectFragment(int layout) {
        super(layout);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        view.findViewById(R.id.vanilla_profile).setOnClickListener(v -> Tools.swapFragment(requireActivity(), ProfileEditorFragment.class,
                ProfileEditorFragment.TAG, new Bundle(1)));

        // NOTE: Special care needed! If you wll decide to add these to the back stack, please read
        // the comment in FabricInstallFragment.onDownloadFinished() and amend the code
        // in FabricInstallFragment.onDownloadFinished() and ModVersionListFragment.onDownloadFinished()
        view.findViewById(R.id.optifine_profile).setOnClickListener(v ->
                tryInstall(OptiFineInstallFragment.class, OptiFineInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_fabric).setOnClickListener((v)->
                tryInstall(FabricInstallFragment.class, FabricInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_forge).setOnClickListener((v)->
                tryInstall(ForgeInstallFragment.class, ForgeInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_neoforge).setOnClickListener((v)->
                tryInstall(NeoForgeInstallFragment.class, NeoForgeInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_modpack).setOnClickListener((v)->
                tryInstall(ModpackCreateFragment.class, ModpackCreateFragment.TAG));
        view.findViewById(R.id.modded_profile_packwiz).setOnClickListener((v)-> askPackwizUrl());
        view.findViewById(R.id.modded_profile_lwjgl3ify).setOnClickListener((v)->
                tryInstall(LWJGL3ifyInstallFragment.class, LWJGL3ifyInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_quilt).setOnClickListener((v)->
                tryInstall(QuiltInstallFragment.class, QuiltInstallFragment.TAG));
        view.findViewById(R.id.modded_profile_bta).setOnClickListener((v)->
                tryInstall(BTAInstallFragment.class, BTAInstallFragment.TAG));
    }

    /** The same import as an amethyst://profile/import link: it fetches the pack, shows it and asks before adding. */
    private void askPackwizUrl() {
        EditText urlInput = new EditText(requireContext());
        urlInput.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        urlInput.setHint(R.string.packwiz_url_hint);
        new AlertDialog.Builder(requireContext())
                .setTitle(R.string.packwiz_profile_url_title)
                .setView(urlInput)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(android.R.string.ok, (dialog, which) -> {
                    String url = urlInput.getText().toString().trim();
                    if (url.isEmpty()) return;
                    Uri link = new Uri.Builder().scheme(getString(R.string.link_scheme)).authority("profile")
                            .path("/import").appendQueryParameter("url", url).build();
                    FragmentActivity activity = requireActivity();
                    LinkRouter.handle(activity, link, () -> Tools.backToMainMenu(activity));
                })
                .show();
    }

    private void tryInstall(Class<? extends Fragment> fragmentClass, String tag){
        if(!hasOnlineProfile()){
            hasNoOnlineProfileDialog(requireActivity());
        } else {
            Tools.swapFragment(requireActivity(), fragmentClass, tag, null);
        }
    }
}
